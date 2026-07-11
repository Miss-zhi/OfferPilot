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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Query 扩展服务。
 * 通过 DashScope LLM 将短关键词扩展为多条检索短语，提升召回率和多样性。
 *
 * <p>输入 "Java并发" → 输出 ["Java并发面试题", "线程池原理", "volatile关键字"]
 *
 * <p>LLM 调用失败时自动回退到规则模式（关键词拆分 + 常见后缀追加）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agentscope.search.query-expansion.enabled", havingValue = "true", matchIfMissing = false)
public class QueryExpansionService {

    private static final String DASHSCOPE_CHAT_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final String SYSTEM_PROMPT =
            "你是一个查询扩展助手。用户输入一个简短的面试准备关键词，" +
            "你需要将其扩展为 3-5 个更具体的检索短语，用于在向量知识库中搜索。\n" +
            "要求：\n" +
            "1. 扩展短语应该覆盖该主题的不同方面和常见面试考点\n" +
            "2. 使用纯 JSON 数组格式输出，不要包含任何额外文字\n" +
            "3. 确保原始查询词包含在结果中\n" +
            "示例：\n" +
            "输入: Java并发\n" +
            "输出: [\"Java并发面试题\", \"线程池原理\", \"volatile关键字\", \"synchronized锁机制\"]";

    /** 常见搜索后缀，用于规则模式兜底 */
    private static final List<String> FALLBACK_SUFFIXES = List.of(
            "面试题", "面试经验", "面经", "学习资料", "教程",
            "常见问题", "高频考点", "技术分享"
    );

    private final String apiKey;
    private final String modelName;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QueryExpansionService(AgentScopeProperties properties) {
        String configuredKey = properties.getModel().getApiKey();
        this.apiKey = (configuredKey != null && !configuredKey.isBlank())
                ? configuredKey
                : System.getenv("DASHSCOPE_API_KEY");
        // 使用轻量模型，仅做扩展不做推理
        this.modelName = "qwen-turbo";
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("QueryExpansionService initialized: model={}", modelName);
    }

    /**
     * 扩展查询关键词，生成多条检索短语。
     *
     * @param query 原始查询词，如 "Java并发"
     * @return 扩展后的查询短语列表（最多 5 条），包含原始查询词
     */
    public List<String> expand(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // 1. 优先使用 DashScope LLM 扩展
        try {
            List<String> llmResult = expandViaLlm(query.trim());
            if (!llmResult.isEmpty()) {
                log.debug("QueryExpansion (LLM): '{}' -> {}", query, llmResult);
                return llmResult;
            }
        } catch (Exception e) {
            log.warn("QueryExpansion LLM failed, falling back to rules: {}", e.getMessage());
        }

        // 2. LLM 失败时回退到规则模式
        List<String> fallback = expandViaRules(query.trim());
        log.debug("QueryExpansion (rules): '{}' -> {}", query, fallback);
        return fallback;
    }

    /**
     * 通过 DashScope LLM 扩展查询。
     */
    private List<String> expandViaLlm(String query) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("max_tokens", 200);
        body.put("temperature", 0.3);

        ArrayNode messages = body.putArray("messages");
        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", SYSTEM_PROMPT);
        messages.add(sysMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", query);
        messages.add(userMsg);

        Request request = new Request.Builder()
                .url(DASHSCOPE_CHAT_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("DashScope chat API error: " + response.code() + " " + err);
            }

            String respBody = response.body().string();
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return List.of();
            }

            String content = choices.get(0).path("message").path("content").asText();
            if (content.isBlank()) {
                return List.of();
            }

            return parseJsonArray(content, query);
        }
    }

    /**
     * 解析 LLM 返回的 JSON 数组，并限制数量。
     */
    private List<String> parseJsonArray(String llmOutput, String originalQuery) {
        try {
            // 清理可能的 markdown 代码块包裹
            String cleaned = llmOutput.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```json?\\s*", "").replaceAll("```", "").trim();
            }

            JsonNode arr = objectMapper.readTree(cleaned);
            if (!arr.isArray()) {
                return List.of();
            }

            Set<String> result = new LinkedHashSet<>();
            result.add(originalQuery);
            for (JsonNode item : arr) {
                String text = item.asText().trim();
                if (!text.isBlank()) {
                    result.add(text);
                }
            }

            List<String> list = new ArrayList<>(result);
            return list.subList(0, Math.min(list.size(), 5));
        } catch (Exception e) {
            log.warn("Failed to parse LLM expansion output: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 规则模式兜底：关键词拆分 + 后缀追加。
     */
    private List<String> expandViaRules(String query) {
        Set<String> expanded = new LinkedHashSet<>();
        expanded.add(query);

        // 拆分多词查询
        String[] tokens = query.split("\\s+");
        if (tokens.length > 1) {
            for (String token : tokens) {
                expanded.add(token);
            }
            for (int i = 0; i < tokens.length - 1; i++) {
                expanded.add(tokens[i] + " " + tokens[i + 1]);
            }
        }

        // 追加后缀
        for (String suffix : FALLBACK_SUFFIXES) {
            expanded.add(query + " " + suffix);
        }

        List<String> result = new ArrayList<>(expanded);
        return result.subList(0, Math.min(result.size(), 5));
    }
}
