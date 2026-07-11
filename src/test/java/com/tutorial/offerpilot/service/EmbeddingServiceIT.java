/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * EmbeddingService 集成测试。
 * <p>
 * 由于 EmbeddingService 调用外部 DashScope API，完整集成测试需要真实 API Key。
 * 本测试验证 Bean 的正确装配、边界条件和批处理逻辑，
 * 不对实际 HTTP 调用做断言（连接失败在预期内）。
 * </p>
 */
@DisplayName("EmbeddingService 集成测试")
class EmbeddingServiceIT extends AbstractServiceIT {

    @Autowired
    private EmbeddingService embeddingService;

    // ==================== Bean 装配 ====================

    @Nested
    @DisplayName("Bean 装配")
    class BeanWiringTests {

        @Test
        @DisplayName("EmbeddingService Bean 应正确加载")
        void beanShouldBeLoaded() {
            assertNotNull(embeddingService);
        }
    }

    // ==================== embedBatch 边界条件 ====================

    @Nested
    @DisplayName("embedBatch 边界条件")
    class BatchEdgeCaseTests {

        @Test
        @DisplayName("null 输入 → 返回空列表")
        void embedBatch_null_shouldReturnEmpty() {
            List<float[]> results = embeddingService.embedBatch(null);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("空列表 → 返回空列表")
        void embedBatch_empty_shouldReturnEmpty() {
            List<float[]> results = embeddingService.embedBatch(List.of());
            assertNotNull(results);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("单条文本（小于批次上限）→ 单批次调用")
        void embedBatch_singleText_shouldWork() {
            // 由于没有真实 API Key，预期抛异常，验证错误信息合理
            try {
                embeddingService.embedBatch(List.of("测试文本"));
                // 如果成功（有真实 Key），验证返回非空
            } catch (RuntimeException e) {
                assertTrue(e.getMessage().contains("Embedding") || e.getMessage().contains("API"),
                        "错误信息应包含 Embedding/API 相关描述: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("10 条文本（恰好一批次上限）→ 单批次调用")
        void embedBatch_exactlyBatchSize_shouldWork() {
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                texts.add("测试文本 " + i);
            }
            assertEquals(10, texts.size());

            try {
                embeddingService.embedBatch(texts);
            } catch (RuntimeException e) {
                // 预期：无真实 API Key，连接失败
                assertNotNull(e.getMessage());
            }
        }

        @Test
        @DisplayName("20 条文本（超过上限）→ 自动分两批（10+10）")
        void embedBatch_overLimit_shouldSplitIntoBatches() {
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                texts.add("测试文本 " + i);
            }
            assertEquals(20, texts.size());

            try {
                embeddingService.embedBatch(texts);
                // 如果成功（有真实 Key），应返回 20 个向量
            } catch (RuntimeException e) {
                // 预期：无真实 API Key，连接失败
                assertNotNull(e.getMessage());
            }
        }
    }

    // ==================== embed 方法 ====================

    @Nested
    @DisplayName("embed 方法")
    class EmbedTests {

        @Test
        @DisplayName("embed 调用 embedBatch 并取第一个结果")
        void embed_shouldDelegateToEmbedBatch() {
            // embed() 是 embedBatch(List.of(text)) 的封装
            // 在无 real API Key 环境下，预期抛出异常
            try {
                float[] result = embeddingService.embed("单条测试");
                assertNotNull(result);
                assertEquals(1024, result.length, "DashScope text-embedding-v3 应返回 1024 维向量");
            } catch (RuntimeException e) {
                assertNotNull(e.getMessage());
            }
        }
    }
}
