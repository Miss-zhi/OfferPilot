/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LLM Provider 模型列表拉取服务。
 * 支持 OpenAI / Anthropic / Gemini 三种 API 格式的响应解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelListFetcher {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据模型配置拉取模型名称列表。
     *
     * @param apiFormat       API 格式: openai / anthropic / gemini
     * @param modelListUrl    模型列表请求 URL
     * @param apiKey          API Key（明文）
     * @param authHeaderType  认证 Header 类型
     * @return 模型名称列表
     */
    public List<String> fetchModelNames(String apiFormat, String modelListUrl,
                                        String apiKey, String authHeaderType) {
        try {
            HttpRequest request = buildRequest(modelListUrl, apiKey, authHeaderType);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Model list API returned status {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("Model list API returned status " + response.statusCode());
            }

            return parseResponse(apiFormat, response.body());
        } catch (Exception e) {
            log.error("Failed to fetch model names from {}: {}", modelListUrl, e.getMessage());
            throw new RuntimeException("Failed to fetch model names: " + e.getMessage(), e);
        }
    }

    private HttpRequest buildRequest(String url, String apiKey, String authHeaderType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET();

        // 根据认证类型设置不同的 Header
        switch (authHeaderType) {
            case "bearer" -> builder.header("Authorization", "Bearer " + apiKey);
            case "x-api-key" -> {
                builder.header("x-api-key", apiKey);
                builder.header("anthropic-version", "2023-06-01");
            }
            case "x-goog-api-key" -> builder.header("x-goog-api-key", apiKey);
            case "none" -> { /* 无需认证 */ }
            default -> builder.header("Authorization", "Bearer " + apiKey);
        }

        return builder.build();
    }

    /**
     * 按 API 格式解析响应 JSON。
     */
    private List<String> parseResponse(String apiFormat, String responseBody) {
        // 未指定格式时默认尝试 OpenAI 格式
        String format = apiFormat != null ? apiFormat : "openai";

        return switch (format) {
            case "anthropic" -> parseAnthropic(responseBody);
            case "gemini" -> parseGemini(responseBody);
            default -> parseOpenAI(responseBody);
        };
    }

    /**
     * OpenAI 格式解析：{ "object": "list", "data": [ { "id": "model-name", ... } ] }
     */
    private List<String> parseOpenAI(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                log.warn("OpenAI format response missing 'data' array");
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode id = item.get("id");
                if (id != null) {
                    names.add(id.asText());
                }
            }
            return names;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI format response", e);
            return Collections.emptyList();
        }
    }

    /**
     * Anthropic 格式解析：{ "data": [ { "id": "claude-xxx", "display_name": "...", ... } ], "has_more": true }
     */
    private List<String> parseAnthropic(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                log.warn("Anthropic format response missing 'data' array");
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode id = item.get("id");
                if (id != null) {
                    names.add(id.asText());
                }
            }
            return names;
        } catch (Exception e) {
            log.error("Failed to parse Anthropic format response", e);
            return Collections.emptyList();
        }
    }

    /**
     * Gemini 格式解析：{ "models": [ { "name": "models/gemini-xxx", ... } ] }
     * 去掉 "models/" 前缀。
     */
    private List<String> parseGemini(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode models = root.get("models");
            if (models == null || !models.isArray()) {
                log.warn("Gemini format response missing 'models' array");
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            for (JsonNode model : models) {
                JsonNode name = model.get("name");
                if (name != null) {
                    String n = name.asText();
                    if (n.startsWith("models/")) {
                        n = n.substring("models/".length());
                    }
                    names.add(n);
                }
            }
            return names;
        } catch (Exception e) {
            log.error("Failed to parse Gemini format response", e);
            return Collections.emptyList();
        }
    }
}
