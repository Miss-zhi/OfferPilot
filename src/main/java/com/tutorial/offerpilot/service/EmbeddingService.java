/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutorial.offerpilot.config.AgentScopeProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Embedding 服务。
 * 默认调用 DashScope text-embedding-v3 API，支持通过 agentscope.embedding.* 独立配置。
 * 当未配置 embedding.api-key 时自动回退使用 agentscope.model.api-key。
 */
@Slf4j
@Service
public class EmbeddingService {

    private final String apiKey;
    private final String modelName;
    private final String embeddingUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_BATCH_SIZE = 10;

    public EmbeddingService(AgentScopeProperties properties) {
        AgentScopeProperties.EmbeddingConfig embeddingConfig = properties.getEmbedding();

        // Embedding API Key 优先级: embedding.api-key > model.api-key（回退兼容）
        String embeddingApiKey = embeddingConfig.getApiKey();
        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            this.apiKey = embeddingApiKey;
        } else {
            this.apiKey = properties.getModel().getApiKey();
            log.info("EmbeddingService using fallback api-key from agentscope.model.api-key");
        }

        this.modelName = properties.getKnowledge().getEmbeddingModel();
        this.embeddingUrl = embeddingConfig.getBaseUrl();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("EmbeddingService initialized: provider={}, model={}, url={}",
                embeddingConfig.getProvider(), modelName, embeddingUrl);
    }

    /**
     * 将单条文本转为向量（DashScope text-embedding-v3，1024 维）。
     */
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        if (results.isEmpty()) {
            throw new RuntimeException("Embedding 返回空结果");
        }
        return results.get(0);
    }

    /**
     * 批量 Embedding，自动按 MAX_BATCH_SIZE=10 分批。
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allResults = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);
            allResults.addAll(embedSingleBatch(batch));
        }
        return allResults;
    }

    /**
     * 单批次 Embedding（最多 MAX_BATCH_SIZE 条）。
     */
    private List<float[]> embedSingleBatch(List<String> texts) {
        try {
            String requestBody = buildRequestBody(texts);
            log.debug("Embedding request: texts={}, bodySize={}", texts.size(), requestBody.length());

            Request request = new Request.Builder()
                    .url(embeddingUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("Embedding API error: status={}, body={}", response.code(), errorBody);
                    throw new RuntimeException("Embedding API 返回错误: " + response.code() + " " + errorBody);
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);

                JsonNode embeddings = root.path("output").path("embeddings");
                if (!embeddings.isArray()) {
                    throw new RuntimeException("Embedding API 返回格式异常: " + responseBody);
                }

                List<float[]> vectors = new ArrayList<>();
                for (JsonNode item : embeddings) {
                    JsonNode embedding = item.path("embedding");
                    float[] vector = new float[embedding.size()];
                    for (int j = 0; j < embedding.size(); j++) {
                        vector[j] = (float) embedding.get(j).asDouble();
                    }
                    vectors.add(vector);
                }

                log.debug("Embedding response: texts={}, vectors={}, dim={}",
                        texts.size(), vectors.size(), vectors.isEmpty() ? 0 : vectors.get(0).length);
                return vectors;
            }
        } catch (IOException e) {
            log.error("Embedding API 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(List<String> texts) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);

        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode textsArray = input.putArray("texts");
        for (String text : texts) {
            textsArray.add(text);
        }
        root.set("input", input);

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("text_type", "document");
        root.set("parameters", parameters);

        return objectMapper.writeValueAsString(root);
    }
}
