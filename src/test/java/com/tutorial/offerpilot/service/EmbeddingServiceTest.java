/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tutorial.offerpilot.config.AgentScopeProperties;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("EmbeddingService 单元测试")
class EmbeddingServiceTest {

    private EmbeddingService embeddingService;
    private OkHttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        AgentScopeProperties properties = new AgentScopeProperties();
        AgentScopeProperties.ModelConfig modelConfig = new AgentScopeProperties.ModelConfig();
        modelConfig.setApiKey("sk-test-key");
        properties.setModel(modelConfig);

        AgentScopeProperties.KnowledgeConfig knowledgeConfig = new AgentScopeProperties.KnowledgeConfig();
        knowledgeConfig.setEmbeddingModel("text-embedding-v3");
        properties.setKnowledge(knowledgeConfig);

        embeddingService = new EmbeddingService(properties);

        mockHttpClient = mock(OkHttpClient.class);
        ReflectionTestUtils.setField(embeddingService, "httpClient", mockHttpClient);
    }

    /** 构造 DashScope Embedding API 成功响应 JSON */
    private String buildSuccessResponse(List<float[]> vectors) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"output\":{\"embeddings\":[");
        for (int i = 0; i < vectors.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"embedding\":[");
            float[] vec = vectors.get(i);
            for (int j = 0; j < vec.length; j++) {
                if (j > 0) sb.append(",");
                sb.append(vec[j]);
            }
            sb.append("]}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    /** 创建模拟成功响应的 Call（先创建全部 mock，避免嵌套 stubbing） */
    private Call createSuccessCall(String responseBody) {
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockBody = mock(ResponseBody.class);

        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.body()).thenReturn(mockBody);
        try {
            when(mockBody.string()).thenReturn(responseBody);
            when(mockCall.execute()).thenReturn(mockResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return mockCall;
    }

    /** 创建模拟错误响应的 Call */
    private Call createErrorCall(int statusCode, String errorBody) {
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockBody = mock(ResponseBody.class);

        when(mockResponse.isSuccessful()).thenReturn(false);
        when(mockResponse.code()).thenReturn(statusCode);
        when(mockResponse.body()).thenReturn(mockBody);
        try {
            when(mockBody.string()).thenReturn(errorBody);
            when(mockCall.execute()).thenReturn(mockResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return mockCall;
    }

    // ==================== embed ====================

    @Nested
    @DisplayName("embed")
    class EmbedTests {

        @Test
        @DisplayName("单条文本 → 返回指定维度的向量")
        void embed_shouldReturnVector() {
            float[] expectedVec = {0.1f, 0.2f, 0.3f, 0.4f};
            Call successCall = createSuccessCall(buildSuccessResponse(List.of(expectedVec)));
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(successCall);

            float[] result = embeddingService.embed("测试文本");

            assertNotNull(result);
            assertEquals(4, result.length);
            assertEquals(0.1f, result[0], 0.001f);
            assertEquals(0.4f, result[3], 0.001f);
        }

        @Test
        @DisplayName("API 返回非 200 → 抛出异常")
        void embed_apiError_shouldThrow() {
            Call errorCall = createErrorCall(401, "{\"message\":\"Invalid API Key\"}");
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(errorCall);

            assertThrows(RuntimeException.class, () -> embeddingService.embed("test"));
        }

        @Test
        @DisplayName("空文本 → Embedding 成功返回向量")
        void embed_emptyText_shouldSucceed() {
            float[] emptyVec = new float[1024];
            Call successCall = createSuccessCall(buildSuccessResponse(List.of(emptyVec)));
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(successCall);

            float[] result = embeddingService.embed("");

            assertNotNull(result);
            assertEquals(1024, result.length);
        }
    }

    // ==================== embedBatch ====================

    @Nested
    @DisplayName("embedBatch")
    class EmbedBatchTests {

        @Test
        @DisplayName("单批次（≤25 条）→ 返回对应数量的向量")
        void embedBatch_singleBatch_shouldReturnAllVectors() {
            float[] v1 = {0.1f, 0.2f};
            float[] v2 = {0.3f, 0.4f};
            float[] v3 = {0.5f, 0.6f};
            Call successCall = createSuccessCall(buildSuccessResponse(List.of(v1, v2, v3)));
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(successCall);

            List<float[]> results = embeddingService.embedBatch(List.of("a", "b", "c"));

            assertEquals(3, results.size());
            assertEquals(0.1f, results.get(0)[0], 0.001f);
            assertEquals(0.3f, results.get(1)[0], 0.001f);
            assertEquals(0.5f, results.get(2)[0], 0.001f);
        }

        @Test
        @DisplayName("多批次（>25 条）→ 自动分批，返回全部向量")
        void embedBatch_multiBatch_shouldSplitAndReturnAll() {
            int totalTexts = 30;
            float[] singleVec = {0.5f};
            List<float[]> batch1Vecs = IntStream.range(0, 25).mapToObj(i -> singleVec).toList();
            List<float[]> batch2Vecs = IntStream.range(0, 5).mapToObj(i -> singleVec).toList();

            Call call1 = createSuccessCall(buildSuccessResponse(batch1Vecs));
            Call call2 = createSuccessCall(buildSuccessResponse(batch2Vecs));
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(call1, call2);

            List<String> texts = IntStream.range(0, totalTexts).mapToObj(i -> "text-" + i).toList();
            List<float[]> results = embeddingService.embedBatch(texts);

            assertEquals(totalTexts, results.size());
        }

        @Test
        @DisplayName("null 输入 → 返回空列表")
        void embedBatch_nullInput_shouldReturnEmpty() {
            List<float[]> results = embeddingService.embedBatch(null);

            assertTrue(results.isEmpty());
            verifyNoInteractions(mockHttpClient);
        }

        @Test
        @DisplayName("空列表输入 → 返回空列表")
        void embedBatch_emptyList_shouldReturnEmpty() {
            List<float[]> results = embeddingService.embedBatch(List.of());

            assertTrue(results.isEmpty());
            verifyNoInteractions(mockHttpClient);
        }

        @Test
        @DisplayName("API 返回格式异常（缺少 output）→ 抛出异常")
        void embedBatch_invalidResponse_shouldThrow() {
            Call badCall = createSuccessCall("{\"code\":200,\"data\":{}}");
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(badCall);

            assertThrows(RuntimeException.class,
                    () -> embeddingService.embedBatch(List.of("test")));
        }

        @Test
        @DisplayName("批量中途 API 失败 → 抛出异常")
        void embedBatch_midApiFail_shouldThrow() {
            float[] singleVec = {0.1f};
            List<float[]> batch1Vecs = IntStream.range(0, 25).mapToObj(i -> singleVec).toList();

            Call call1 = createSuccessCall(buildSuccessResponse(batch1Vecs));
            Call call2 = createErrorCall(500, "Internal Server Error");
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(call1, call2);

            List<String> texts = IntStream.range(0, 30).mapToObj(i -> "text-" + i).toList();

            assertThrows(RuntimeException.class, () -> embeddingService.embedBatch(texts));
        }

        @Test
        @DisplayName("单条文本 embedBatch → 委托给 embedSingleBatch")
        void embedBatch_singleText_shouldReturnSingleVector() {
            float[] vec = {0.7f, 0.8f};
            Call successCall = createSuccessCall(buildSuccessResponse(List.of(vec)));
            when(mockHttpClient.newCall(any(Request.class))).thenReturn(successCall);

            List<float[]> results = embeddingService.embedBatch(List.of("hello"));

            assertEquals(1, results.size());
            assertEquals(0.7f, results.get(0)[0], 0.001f);
        }
    }
}
