/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.AnswerSearchResult;
import com.tutorial.offerpilot.dto.tool.KnowledgeGapResult;
import com.tutorial.offerpilot.dto.tool.SearchRequest;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeGapTool 单元测试")
class KnowledgeGapToolTest {

    @Mock
    private KnowledgeBaseService kbService;

    @InjectMocks
    private KnowledgeGapTool tool;

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("detectGaps - 边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("question 为 null → 返回错误指导 + 空列表")
        void nullQuestion_shouldReturnErrorGuidance() {
            KnowledgeGapResult result = tool.detectGaps(null, "user answer", null);

            assertEquals("问题为空，无法检测知识盲区", result.getGuidance());
            assertTrue(result.getCovered().isEmpty());
            assertTrue(result.getMissing().isEmpty());
            assertEquals(0, result.getCoverageRate());
        }

        @Test
        @DisplayName("question 为空字符串 → 返回错误指导")
        void blankQuestion_shouldReturnErrorGuidance() {
            KnowledgeGapResult result = tool.detectGaps("   ", "user answer", null);

            assertEquals("问题为空，无法检测知识盲区", result.getGuidance());
            assertEquals(0, result.getCoverageRate());
        }

        @Test
        @DisplayName("user_answer 为 null → 返回错误指导")
        void nullUserAnswer_shouldReturnErrorGuidance() {
            KnowledgeGapResult result = tool.detectGaps("什么是GC", null, null);

            assertEquals("用户回答为空，无法检测知识盲区", result.getGuidance());
            assertTrue(result.getCovered().isEmpty());
            assertTrue(result.getMissing().isEmpty());
            assertEquals(0, result.getCoverageRate());
        }

        @Test
        @DisplayName("user_answer 为空字符串 → 返回错误指导")
        void blankUserAnswer_shouldReturnErrorGuidance() {
            KnowledgeGapResult result = tool.detectGaps("什么是GC", "   ", null);

            assertEquals("用户回答为空，无法检测知识盲区", result.getGuidance());
            assertEquals(0, result.getCoverageRate());
        }
    }

    // ==================== 概念提取与对比 ====================

    @Nested
    @DisplayName("detectGaps - 概念提取与对比")
    class ConceptExtractionTests {

        @Test
        @DisplayName("RAG 检索为空 → 覆盖率为 0")
        void emptyRagResult_shouldReturnZeroCoverage() {
            AnswerSearchResult emptyResult = new AnswerSearchResult(0, List.of());
            when(kbService.searchAnswers(any(SearchRequest.class))).thenReturn(emptyResult);

            KnowledgeGapResult result = tool.detectGaps("什么是Java", "Java是面向对象语言", 3);

            assertEquals(0, result.getCoverageRate());
            assertTrue(result.getCovered().isEmpty());
            assertTrue(result.getMissing().isEmpty());
            assertTrue(result.getGuidance().contains("词法覆盖率：0%"));
        }

        @Test
        @DisplayName("标答包含专有名词，用户回答全覆盖 → 覆盖率 100%")
        void fullCoverage_shouldReturn100Percent() {
            AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
            item.setAnswer("Java内存模型（JMM）定义了主内存与工作内存之间的交互协议。");
            AnswerSearchResult ragResult = new AnswerSearchResult(1, List.of(item));
            when(kbService.searchAnswers(any(SearchRequest.class))).thenReturn(ragResult);

            KnowledgeGapResult result = tool.detectGaps(
                    "什么是Java内存模型",
                    "Java内存模型（JMM）定义了主内存与工作内存之间的交互协议", 3);

            assertEquals(100, result.getCoverageRate());
            assertFalse(result.getCovered().isEmpty());
            assertTrue(result.getMissing().isEmpty());
        }

        @Test
        @DisplayName("用户回答遗漏标答中的关键术语 → 检测到遗漏")
        void partialCoverage_shouldDetectGaps() {
            AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
            item.setAnswer("GC使用标记-清除算法和分代回收模式。JVM调优需要关注GC停顿时间。");
            AnswerSearchResult ragResult = new AnswerSearchResult(1, List.of(item));
            when(kbService.searchAnswers(any(SearchRequest.class))).thenReturn(ragResult);

            KnowledgeGapResult result = tool.detectGaps(
                    "什么是GC",
                    "GC就是垃圾回收", 3);

            assertTrue(result.getCoverageRate() < 100);
            assertFalse(result.getMissing().isEmpty());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("词法覆盖率"));
        }

        @Test
        @DisplayName("标答中的答案字段为 null → 跳过该条目")
        void nullAnswerItem_shouldSkip() {
            AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
            item.setAnswer(null);
            AnswerSearchResult ragResult = new AnswerSearchResult(1, List.of(item));
            when(kbService.searchAnswers(any(SearchRequest.class))).thenReturn(ragResult);

            KnowledgeGapResult result = tool.detectGaps("GC", "回答", 3);

            assertEquals(0, result.getCoverageRate());
        }

        @Test
        @DisplayName("top_k 参数为 null → 使用默认值 3")
        void nullTopK_shouldDefaultTo3() {
            when(kbService.searchAnswers(any(SearchRequest.class)))
                    .thenReturn(new AnswerSearchResult(0, List.of()));

            // Should not throw
            KnowledgeGapResult result = tool.detectGaps("question", "answer", null);
            assertNotNull(result);
        }
    }

    // ==================== guidance 内容 ====================

    @Nested
    @DisplayName("detectGaps - guidance 内容")
    class GuidanceTests {

        @Test
        @DisplayName("guidance 应包含语义复核提示")
        void guidance_shouldContainSemanticHints() {
            AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
            item.setAnswer("设计模式如单例模式、工厂模式是常见考点。");
            AnswerSearchResult ragResult = new AnswerSearchResult(1, List.of(item));
            when(kbService.searchAnswers(any(SearchRequest.class))).thenReturn(ragResult);

            KnowledgeGapResult result = tool.detectGaps("设计模式", "我知道单例", 3);

            assertTrue(result.getGuidance().contains("语义层面复核"));
            assertTrue(result.getGuidance().contains("词法对比仅检测表面术语匹配"));
        }
    }
}
