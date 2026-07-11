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
import java.util.Optional;

/**
 * 联网搜索兜底服务。
 * 通过 Streamable HTTP 调用 open-websearch MCP 服务的 search 工具，
 * 作为 Milvus 向量检索和 DB LIKE 检索均无结果时的兜底。
 */
@Slf4j
@Service
public class WebSearchFallbackService {

    private static final String MCP_URL = "http://localhost:3000/mcp";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_RESULTS = 5;
    /** MCP 服务器实际注册的工具名（非 web_search） */
    private static final String TOOL_NAME = "search";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 缓存的 MCP 会话 ID，惰性初始化 */
    private volatile String cachedSessionId;
    private volatile long sessionExpireTime;

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
            // 1. 获取或复用 MCP 会话
            String sessionId = getOrCreateSession();
            if (sessionId == null) {
                log.warn("WebSearchFallback: failed to obtain MCP session");
                return Collections.emptyList();
            }

            // 2. 构建 MCP JSON-RPC tools/call 请求
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", "1");
            requestBody.put("method", "tools/call");

            ObjectNode params = requestBody.putObject("params");
            params.put("name", TOOL_NAME);

            ObjectNode arguments = params.putObject("arguments");
            arguments.put("query", query);
            arguments.put("limit", maxResults);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 3. 通过 Streamable HTTP 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MCP_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("Mcp-Session-Id", sessionId)
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
     * 获取或创建 MCP 会话。
     * 通过 Streamable HTTP 发送 initialize 请求获取 Mcp-Session-Id。
     */
    private String getOrCreateSession() {
        // 检查缓存的会话是否有效
        if (cachedSessionId != null && System.currentTimeMillis() < sessionExpireTime) {
            return cachedSessionId;
        }

        synchronized (this) {
            if (cachedSessionId != null && System.currentTimeMillis() < sessionExpireTime) {
                return cachedSessionId;
            }

            try {
                ObjectNode initBody = objectMapper.createObjectNode();
                initBody.put("jsonrpc", "2.0");
                initBody.put("id", "init");
                initBody.put("method", "initialize");
                ObjectNode initParams = initBody.putObject("params");
                initParams.put("protocolVersion", "2024-11-05");
                initParams.putObject("capabilities");
                ObjectNode clientInfo = initParams.putObject("clientInfo");
                clientInfo.put("name", "offerpilot-websearch-fallback");
                clientInfo.put("version", "1.0");

                HttpRequest initRequest = HttpRequest.newBuilder()
                        .uri(URI.create(MCP_URL))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .timeout(SESSION_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(initBody)))
                        .build();

                HttpResponse<String> initResponse = httpClient.send(
                        initRequest, HttpResponse.BodyHandlers.ofString());

                if (initResponse.statusCode() != 200) {
                    log.warn("WebSearchFallback: initialize failed with HTTP {}",
                            initResponse.statusCode());
                    return null;
                }

                String sessionId = initResponse.headers()
                        .firstValue("Mcp-Session-Id")
                        .or(() -> initResponse.headers().firstValue("mcp-session-id"))
                        .orElse(null);

                if (sessionId != null) {
                    cachedSessionId = sessionId;
                    sessionExpireTime = System.currentTimeMillis() + 300_000; // 5分钟过期
                    log.info("WebSearchFallback: MCP session established: {}",
                            sessionId.substring(0, 8) + "...");
                } else {
                    log.warn("WebSearchFallback: no Mcp-Session-Id in initialize response");
                }

                return sessionId;
            } catch (Exception e) {
                log.warn("WebSearchFallback: session init failed: {}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * 解析 MCP Streamable HTTP 响应（SSE 格式），提取搜索结果。
     * 响应格式: event: message\ndata: {...json...}
     */
    private List<WebSearchItem> parseResponse(String responseBody) {
        try {
            // 提取 SSE data 行中的 JSON
            String jsonStr = extractSseData(responseBody);
            if (jsonStr == null) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(jsonStr);

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
                    String textContent = text.asText();
                    // text 字段本身是 JSON 字符串，包含 results 数组
                    items.addAll(parseSearchResults(textContent));
                }
            }

            log.info("WebSearchFallback: query returned {} results", items.size());
            return items;
        } catch (Exception e) {
            log.warn("WebSearchFallback: failed to parse response - {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 SSE 格式响应中提取 data 行的 JSON 内容。
     */
    private String extractSseData(String sseBody) {
        for (String line : sseBody.split("\n")) {
            if (line.startsWith("data:")) {
                return line.substring(5).trim();
            }
        }
        // 如果无法解析为 SSE，尝试当作纯 JSON
        return sseBody.trim().startsWith("{") ? sseBody.trim() : null;
    }

    /**
     * 解析搜索结果的 text 字段（本身是 JSON），提取 results 数组中的 title + url + description。
     */
    private List<WebSearchItem> parseSearchResults(String textJson) {
        List<WebSearchItem> items = new ArrayList<>();
        try {
            JsonNode searchResult = objectMapper.readTree(textJson);
            JsonNode results = searchResult.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode r : results) {
                    WebSearchItem item = new WebSearchItem();
                    String title = r.has("title") ? r.get("title").asText() : "";
                    String url = r.has("url") ? r.get("url").asText() : "";
                    String desc = r.has("description") ? r.get("description").asText() : "";
                    item.setContent(title + "\n" + desc + "\n" + url);
                    item.setSource("web");
                    items.add(item);
                }
            }
        } catch (Exception e) {
            // 如果 text 不是 JSON，直接作为纯文本内容
            WebSearchItem item = new WebSearchItem();
            item.setContent(truncate(textJson, 200));
            item.setSource("web");
            items.add(item);
        }
        return items;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Data
    public static class WebSearchItem {
        private String content;
        private String source;
    }
}
