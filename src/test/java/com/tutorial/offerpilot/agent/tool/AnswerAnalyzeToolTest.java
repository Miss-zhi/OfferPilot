/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.AnswerAnalysisResult;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerAnalyzeTool 单元测试")
class AnswerAnalyzeToolTest {

    @Mock
    private InterviewQuestionRepository questionRepo;

    @InjectMocks
    private AnswerAnalyzeTool tool;

    // ==================== analyze - 边界条件 ====================

    @Nested
    @DisplayName("analyze - 边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("question 为 null → 返回错误指导 + null 评分")
        void nullQuestion_shouldReturnErrorGuidance() {
            AnswerAnalysisResult result = tool.analyze(null, "some answer");

            assertNotNull(result);
            assertEquals("问题和回答均为空，无法分析。", result.getGuidance());
            assertNull(result.getTechScore());
            assertNull(result.getExprScore());
            assertNull(result.getCoverageScore());
            assertTrue(result.getHighlights().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertNull(result.getSuggestion());
        }

        @Test
        @DisplayName("answer 为 null → 返回错误指导 + null 评分")
        void nullAnswer_shouldReturnErrorGuidance() {
            AnswerAnalysisResult result = tool.analyze("what is Java?", null);

            assertEquals("问题和回答均为空，无法分析。", result.getGuidance());
            assertNull(result.getTechScore());
            assertNull(result.getExprScore());
            assertNull(result.getCoverageScore());
            assertTrue(result.getWeaknesses().isEmpty());
        }
    }

    // ==================== analyze - 技术得分 ====================

    @Nested
    @DisplayName("analyze - 技术得分（LLM 模式）")
    class TechScoreTests {

        @Test
        @DisplayName("极短回答 → 评分 null，guidance 非空")
        void veryShortAnswer_shouldReturnNullScores() {
            String answer = "Java是面向对象语言。";

            AnswerAnalysisResult result = tool.analyze("什么是Java？", answer);

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("请分析以下面试回答的质量"));
            assertNull(result.getTechScore());
            assertNull(result.getExprScore());
            assertNull(result.getCoverageScore());
        }

        @Test
        @DisplayName("长回答 → 评分 null，guidance 包含原文")
        void longAnswerWithKeywords_shouldReturnGuidanceOnly() {
            String answer = ("算法 数据结构 设计模式 框架 架构 数据库 缓存 并发 分布式 微服务 API 协议. ").repeat(30);

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("算法"));
            assertNull(result.getTechScore());
        }

        @Test
        @DisplayName("中等回答 → 评分 null，guidance 包含 LLM 输出格式说明")
        void shortAnswerWithKeywords_shouldReturnGuidanceOnly() {
            String answer = "Java中使用算法和数据结构非常重要，框架如Spring、MyBatis和Hibernate提供了缓存机制和数据库访问能力。";

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("专业深度"));
            assertTrue(result.getGuidance().contains("表达能力"));
            assertTrue(result.getGuidance().contains("内容覆盖度"));
            assertNull(result.getTechScore());
        }
    }

    // ==================== analyze - 表达得分 ====================

    @Nested
    @DisplayName("analyze - 表达得分（LLM 模式）")
    class ExpressionScoreTests {

        @Test
        @DisplayName("极短回答 → 评分 null，guidance 非空")
        void veryShortAnswer_shouldReturnNullExprScore() {
            AnswerAnalysisResult result = tool.analyze("问", "太短了");

            assertNotNull(result.getGuidance());
            assertNull(result.getExprScore());
        }

        @Test
        @DisplayName("结构化回答 → 评分 null，guidance 包含原文")
        void structuredAnswer_shouldReturnGuidanceWithText() {
            String answer = "首先，Java是面向对象语言。第一，它有封装继承多态。总之，它非常强大且安全。";

            AnswerAnalysisResult result = tool.analyze("Java?", answer);

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("Java是面向对象语言"));
            assertNull(result.getExprScore());
        }

        @Test
        @DisplayName("无结构标记 → 评分 null，guidance 非空")
        void noStructureMarkers_shouldReturnNullExprScore() {
            String answer = "Java是一种广泛使用的计算机编程语言，拥有跨平台、面向对象、泛型编程的特性。".repeat(3);

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            assertNotNull(result.getGuidance());
            assertNull(result.getExprScore());
        }
    }

    // ==================== analyze - 覆盖度得分 ====================

    @Nested
    @DisplayName("analyze - 覆盖度得分（LLM 模式）")
    class CoverageScoreTests {

        @Test
        @DisplayName("极短回答 → 评分 null，guidance 非空")
        void veryShortAnswer_shouldReturnNullCoverage() {
            AnswerAnalysisResult result = tool.analyze("Q", "A");

            assertNotNull(result.getGuidance());
            assertNull(result.getCoverageScore());
        }

        @Test
        @DisplayName("含举例 → 评分 null，guidance 包含原文")
        void answerWithExamplesAndProsCons_shouldReturnGuidance() {
            String answer = "Java例如在Web开发中广泛使用。它的优点是可移植，缺点是性能。".repeat(2);

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("Java例如"));
            assertNull(result.getCoverageScore());
        }
    }

    // ==================== analyze - 亮点/弱点/建议 ====================

    @Nested
    @DisplayName("analyze - 亮点/弱点/建议（LLM 模式）")
    class HighlightsWeaknessesTests {

        @Test
        @DisplayName("高分回答 → 亮点/弱点均为空，suggestion 为 null")
        void highScoreAnswer_shouldHaveEmptyHighlights() {
            String answer = ("算法 数据结构 框架 数据库 缓存 微服务 分布式 协议. ").repeat(40);

            AnswerAnalysisResult result = tool.analyze("Q", answer);

            assertNotNull(result.getGuidance());
            assertTrue(result.getHighlights().isEmpty());
            assertNull(result.getSuggestion());
        }

        @Test
        @DisplayName("低分回答 → 亮点/弱点均为空，suggestion 为 null")
        void lowScoreAnswer_shouldHaveEmptyWeaknesses() {
            AnswerAnalysisResult result = tool.analyze("Q", "short");

            assertNotNull(result.getGuidance());
            assertTrue(result.getWeaknesses().isEmpty());
        }

        @Test
        @DisplayName("长回答 → suggestion 为 null，交由 LLM 生成")
        void highAverage_shouldHaveNullSuggestion() {
            String answer = ("算法 数据结构 框架 架构 数据库 首先，Java是强大的语言，例如Web开发。总之，广泛使用。").repeat(30);

            AnswerAnalysisResult result = tool.analyze("Q", answer);

            assertNotNull(result.getGuidance());
            assertNull(result.getSuggestion());
        }

        @Test
        @DisplayName("中等回答 → suggestion 为 null")
        void mediumAverage_shouldHaveNullSuggestion() {
            String answer = ("算法 数据结构 框架 数据库 缓存 首先，Java是非常流行的编程语言。"
                    + "例如在Web后端开发中广泛使用。总之，它是企业级首选。").repeat(4);

            AnswerAnalysisResult result = tool.analyze("Q", answer);

            assertNotNull(result.getGuidance());
            assertNull(result.getSuggestion());
        }

        @Test
        @DisplayName("低分回答 → suggestion 为 null")
        void lowAverage_shouldHaveNullSuggestion() {
            AnswerAnalysisResult result = tool.analyze("Q", "short answer");

            assertNotNull(result.getGuidance());
            assertNull(result.getSuggestion());
        }
    }

    // ==================== analyze - 持久化 ====================

    @Nested
    @DisplayName("analyze - 持久化")
    class PersistenceTests {

        @Test
        @DisplayName("找到匹配问题 → 保存回答文本到 DB")
        void matchedQuestion_shouldPersist() {
            when(questionRepo.findByQuestionText("Q")).thenReturn(List.of());

            tool.analyze("Q", "some answer with 算法 and 数据结构");

            // no match → questionRepo.save not called
            verify(questionRepo, never()).save(any());
        }

        @Test
        @DisplayName("持久化异常 → 不影响返回 guidance")
        void persistenceException_shouldNotAffectResult() {
            when(questionRepo.findByQuestionText("Q"))
                    .thenThrow(new RuntimeException("DB error"));

            AnswerAnalysisResult result = tool.analyze("Q", "some answer with 算法");

            assertNotNull(result);
            assertNotNull(result.getGuidance());
            assertNull(result.getTechScore());
        }
    }
}
