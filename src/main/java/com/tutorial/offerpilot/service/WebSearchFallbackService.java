/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
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
 * 联网搜索兜底服务。
 * 通过 HTTP 直接调用 open-websearch MCP 服务的 web_search 工具，
 * 作为 Milvus 向量检索和 DB LIKE 检索均无结果时的兜底。
 */
@Slf4j
@Service
public class WebSearchFallbackService {

    private static final String MCP_URL = "http://localhost:3000/mcp";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchFallbackService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行联网搜索兜底。
     *
     * @param query 搜索关键词
     * @return 搜索结果列表，失败时返回空列表
     */
    public List<WebSearchItem> search(String query) {
        return search(query, DEFAULT_MAX_RESULTS);
    }

    /**
     * 执行联网搜索兜底。
     *
     * @param query      搜索关键词
     * @param maxResults 最大返回数量
     * @return 搜索结果列表，失败时返回空列表
     */
    public List<WebSearchItem> search(String query, int maxResults) {
        try {
            // 构建 MCP JSON-RPC tools/call 请求
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", "1");
            requestBody.put("method", "tools/call");

            ObjectNode params = requestBody.putObject("params");
            params.put("name", "web_search");

            ObjectNode arguments = params.putObject("arguments");
            arguments.put("query", query);
            arguments.put("max_results", maxResults);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MCP_URL))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("WebSearchFallback: HTTP {} from MCP service", response.statusCode());
                return Collections.emptyList();
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            log.warn("WebSearchFallback failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 MCP JSON-RPC 响应，提取搜索结果。
     */
    private List<WebSearchItem> parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 检查 JSON-RPC 错误
            if (root.has("error")) {
                log.warn("WebSearchFallback: MCP error - {}", root.get("error"));
                return Collections.emptyList();
            }

            JsonNode result = root.get("result");
            if (result == null) {
                return Collections.emptyList();
            }

            // MCP tools/call 响应格式: result.content[].text
            JsonNode content = result.get("content");
            if (content == null || !content.isArray()) {
                return Collections.emptyList();
            }

            List<WebSearchItem> items = new ArrayList<>();
            for (JsonNode item : content) {
                JsonNode text = item.get("text");
                if (text != null) {
                    WebSearchItem wsItem = new WebSearchItem();
                    wsItem.setContent(text.asText());
                    wsItem.setSource("web");
                    items.add(wsItem);
                }
            }

            log.info("WebSearchFallback: query returned {} results", items.size());
            return items;
        } catch (Exception e) {
            log.warn("WebSearchFallback: failed to parse response - {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Data
    public static class WebSearchItem {
        private String content;
        private String source;
    }
}
