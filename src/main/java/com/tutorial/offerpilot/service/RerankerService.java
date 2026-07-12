/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutorial.offerpilot.config.AgentScopeProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Rerank 精排服务。
 * 调用 DashScope Rerank API 对多路召回结果进行语义相关性重排序，
 * 提升最终返回结果的精度。
 *
 * <p>Rerank 流程：召回候选 → 送入 Rerank 模型 → 按 relevanceScore 降序 → 取 Top-N。
 *
 * <p>失败降级：API 调用失败时直接返回原始顺序，不阻断检索链路。
 * 可通过 agentscope.rerank.enabled=false 关闭精排。
 */
@Slf4j
@Service
public class RerankerService {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_DOCUMENTS = 50;

    private final boolean enabled;
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final int defaultTopN;
    private final double scoreThreshold;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RerankerService(AgentScopeProperties properties) {
        AgentScopeProperties.RerankConfig config = properties.getRerank();

        // API Key 优先级: rerank.api-key > model.api-key（回退兼容）
        String configuredKey = config.getApiKey();
        if (configuredKey != null && !configuredKey.isBlank()) {
            this.apiKey = configuredKey;
        } else {
            this.apiKey = properties.getModel().getApiKey();
            if (this.apiKey != null && !this.apiKey.isBlank()) {
                log.info("RerankerService using fallback api-key from agentscope.model.api-key");
            }
        }

        // 最终 apiKey 无效时强制禁用，避免无效 HTTP 请求
        if (this.apiKey == null || this.apiKey.isBlank()) {
            log.warn("RerankerService has no valid API key, disabling rerank");
            this.enabled = false;
        } else {
            this.enabled = config.isEnabled();
        }

        this.modelName = config.getModelName();
        this.baseUrl = config.getBaseUrl();
        this.defaultTopN = config.getTopN();
        this.scoreThreshold = config.getScoreThreshold();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("RerankerService initialized: enabled={}, model={}, baseUrl={}, topN={}",
                enabled, modelName, baseUrl, defaultTopN);
    }

    /**
     * Rerank 单条结果。
     */
    @Data
    public static class RerankResult {
        /** 原始文档在输入列表中的索引 */
        private int index;
        /** Rerank 相关性分数，范围 [0, 1]，越高越相关 */
        private double relevanceScore;
        /** 文档内容 */
        private String document;

        public static RerankResult of(int index, double score, String document) {
            RerankResult r = new RerankResult();
            r.index = index;
            r.relevanceScore = score;
            r.document = document;
            return r;
        }
    }

    /**
     * 对候选文档列表进行 Rerank 精排。
     *
     * @param query     用户查询文本
     * @param documents 候选文档内容列表（最多 50 条，超出自动截断）
     * @param topN      精排后保留数量，为 null 时使用配置默认值
     * @return 按 relevanceScore 降序排列的精排结果列表
     */
    public List<RerankResult> rerank(String query, List<String> documents, Integer topN) {
        if (!enabled) {
            log.debug("Rerank disabled, returning original order");
            return fallbackOrder(documents);
        }

        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return fallbackOrder(documents);
        }

        // 限制文档数量
        List<String> docs = documents;
        if (docs.size() > MAX_DOCUMENTS) {
            docs = docs.subList(0, MAX_DOCUMENTS);
        }

        int n = topN != null ? topN : defaultTopN;

        try {
            List<RerankResult> results = callRerankApi(query, docs);
            // 过滤低分结果
            if (scoreThreshold > 0) {
                results = results.stream()
                        .filter(r -> r.relevanceScore >= scoreThreshold)
                        .collect(Collectors.toList());
            }
            // 截断到 topN
            if (results.size() > n) {
                results = results.subList(0, n);
            }
            log.debug("Rerank complete: query={}, inputDocs={}, outputDocs={}", query, docs.size(), results.size());
            return results;
        } catch (Exception e) {
            log.warn("Rerank API failed, falling back to original order: {}", e.getMessage());
            List<RerankResult> fallback = fallbackOrder(docs);
            return fallback.size() > n ? fallback.subList(0, n) : fallback;
        }
    }

    /**
     * 调用 DashScope Rerank API（OpenAI 兼容端点）。
     */
    private List<RerankResult> callRerankApi(String query, List<String> documents) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("query", query);

        ArrayNode docsArray = body.putArray("documents");
        for (String doc : documents) {
            docsArray.add(doc);
        }

        Request request = new Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("Rerank API error: " + response.code() + " " + err);
            }

            String respBody = response.body().string();
            JsonNode root = objectMapper.readTree(respBody);

            JsonNode resultsNode = root.path("results");
            if (!resultsNode.isArray()) {
                throw new IOException("Rerank API unexpected response: " + respBody);
            }

            List<RerankResult> results = new ArrayList<>();
            for (JsonNode item : resultsNode) {
                int index = item.path("index").asInt();
                double score = item.path("relevance_score").asDouble();
                String doc = index < documents.size() ? documents.get(index) : "";
                results.add(RerankResult.of(index, score, doc));
            }

            // 按相关性分数降序排列
            results.sort((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore));
            return results;
        }
    }

    /**
     * 降级：保持原始顺序，赋予均匀递减分数。
     */
    private List<RerankResult> fallbackOrder(List<String> documents) {
        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            double score = 1.0 - (i * 0.01); // 均匀递减，保持顺序
            results.add(RerankResult.of(i, Math.max(0, score), documents.get(i)));
        }
        return results;
    }
}
