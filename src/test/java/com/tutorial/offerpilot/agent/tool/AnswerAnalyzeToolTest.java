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

    @Nested
    @DisplayName("analyze - 边界条件")
    class EdgeCaseTests {
        @Test
        @DisplayName("question 为 null → 返回错误指导")
        void nullQuestion_shouldReturnErrorGuidance() {
            AnswerAnalysisResult result = tool.analyze(null, "some answer", null);
            assertNotNull(result);
            assertEquals("问题和回答均为空，无法分析。", result.getGuidance());
            assertNull(result.getTechScore());
        }
        @Test
        @DisplayName("answer 为 null → 返回错误指导")
        void nullAnswer_shouldReturnErrorGuidance() {
            AnswerAnalysisResult result = tool.analyze("what is Java?", null, null);
            assertEquals("问题和回答均为空，无法分析。", result.getGuidance());
        }
    }

    @Nested
    @DisplayName("analyze - 技术得分")
    class TechScoreTests {
        @Test
        @DisplayName("极短回答 → 评分 null，guidance 非空")
        void veryShortAnswer_shouldReturnNullScores() {
            AnswerAnalysisResult result = tool.analyze("什么是Java？", "Java是面向对象语言。", null);
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("请分析以下面试回答的质量"));
            assertNull(result.getTechScore());
        }
        @Test
        @DisplayName("长回答 → guidance 包含原文")
        void longAnswer_shouldReturnGuidanceOnly() {
            String answer = ("算法 数据结构 设计模式 框架 架构 数据库 缓存 并发 分布式 微服务 API 协议. ").repeat(30);
            AnswerAnalysisResult result = tool.analyze("Java", answer, null);
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("算法"));
            assertNull(result.getTechScore());
        }
        @Test
        @DisplayName("中等回答 → guidance 含 LLM 输出格式")
        void mediumAnswer_shouldReturnGuidance() {
            String answer = "Java中使用算法和数据结构非常重要，框架如Spring、MyBatis和Hibernate提供了缓存和数据库访问能力。";
            AnswerAnalysisResult result = tool.analyze("Java", answer, null);
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("专业深度"));
            assertTrue(result.getGuidance().contains("表达能力"));
            assertTrue(result.getGuidance().contains("内容覆盖度"));
            assertNull(result.getTechScore());
        }
    }

    @Nested
    @DisplayName("analyze - 表达得分")
    class ExpressionScoreTests {
        @Test
        @DisplayName("极短回答 → exprScore null")
        void veryShort_shouldReturnNull() {
            AnswerAnalysisResult result = tool.analyze("问", "太短了", null);
            assertNotNull(result.getGuidance());
            assertNull(result.getExprScore());
        }
        @Test
        @DisplayName("结构化回答 → guidance 包含原文")
        void structured_shouldReturnGuidance() {
            String answer = "首先，Java是面向对象语言。第一，它有封装继承多态。总之，它非常强大且安全。";
            AnswerAnalysisResult result = tool.analyze("Java?", answer, null);
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("Java是面向对象语言"));
            assertNull(result.getExprScore());
        }
        @Test
        @DisplayName("无结构标记 → exprScore null")
        void noStructure_shouldReturnNull() {
            String answer = "Java是一种广泛使用的计算机编程语言，拥有跨平台、面向对象、泛型编程的特性。".repeat(3);
            AnswerAnalysisResult result = tool.analyze("Java", answer, null);
            assertNotNull(result.getGuidance());
            assertNull(result.getExprScore());
        }
    }

    @Nested
    @DisplayName("analyze - 覆盖度得分")
    class CoverageScoreTests {
        @Test
        @DisplayName("极短回答 → coverageScore null")
        void veryShort_shouldReturnNull() {
            AnswerAnalysisResult result = tool.analyze("Q", "A", null);
            assertNotNull(result.getGuidance());
            assertNull(result.getCoverageScore());
        }
        @Test
        @DisplayName("含举例 → guidance 包含原文")
        void withExamples_shouldReturnGuidance() {
            String answer = "Java例如在Web开发中广泛使用。它的优点是可移植，缺点是性能。".repeat(2);
            AnswerAnalysisResult result = tool.analyze("Java", answer, null);
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("Java例如"));
            assertNull(result.getCoverageScore());
        }
    }

    @Nested
    @DisplayName("analyze - 亮点/弱点/建议")
    class HighlightsWeaknessesTests {
        @Test
        @DisplayName("高分回答 → empty highlights, null suggestion")
        void highScore_shouldEmptyHighlights() {
            String answer = ("算法 数据结构 框架 数据库 缓存 微服务 分布式 协议. ").repeat(40);
            AnswerAnalysisResult result = tool.analyze("Q", answer, null);
            assertNotNull(result.getGuidance());
            assertTrue(result.getHighlights().isEmpty());
            assertNull(result.getSuggestion());
        }
        @Test
        @DisplayName("低分回答 → empty weaknesses")
        void lowScore_shouldEmptyWeaknesses() {
            AnswerAnalysisResult result = tool.analyze("Q", "short", null);
            assertNotNull(result.getGuidance());
            assertTrue(result.getWeaknesses().isEmpty());
        }
        @Test
        @DisplayName("长回答 → null suggestion")
        void longAnswer_shouldNullSuggestion() {
            String answer = ("算法 数据结构 框架 架构 数据库 首先，Java是强大的语言。").repeat(30);
            AnswerAnalysisResult result = tool.analyze("Q", answer, null);
            assertNotNull(result.getGuidance());
            assertNull(result.getSuggestion());
        }
        @Test
        @DisplayName("中等回答 → null suggestion")
        void medium_shouldNullSuggestion() {
            String answer = ("算法 数据结构 框架 数据库 缓存 首先，Java是流行的语言。例如在Web开发中广泛使用。总之，是首选。").repeat(4);
            AnswerAnalysisResult result = tool.analyze("Q", answer, null);
            assertNotNull(result.getGuidance());
            assertNull(result.getSuggestion());
        }
        @Test
        @DisplayName("低分回答 → null suggestion")
        void low_shouldNullSuggestion() {
            AnswerAnalysisResult result = tool.analyze("Q", "short answer", null);
            assertNotNull(result.getGuidance());
            assertNull(result.getSuggestion());
        }
    }

    @Nested
    @DisplayName("analyze - 持久化")
    class PersistenceTests {
        @Test
        @DisplayName("无匹配问题 → 不保存")
        void noMatch_shouldNotPersist() {
            when(questionRepo.findByQuestionText("Q")).thenReturn(List.of());
            tool.analyze("Q", "some answer", null);
            verify(questionRepo, never()).save(any());
        }
        @Test
        @DisplayName("持久化异常 → 不影响返回")
        void persistenceException_shouldNotAffectResult() {
            when(questionRepo.findByQuestionText("Q")).thenThrow(new RuntimeException("DB error"));
            AnswerAnalysisResult result = tool.analyze("Q", "some answer", null);
            assertNotNull(result);
            assertNotNull(result.getGuidance());
            assertNull(result.getTechScore());
        }
    }

    @Nested
    @DisplayName("analyze - 压力模式")
    class PressureModeTests {
        @Test
        @DisplayName("PRESSURE → followUpGuidance 非空含追问指导")
        void pressure_shouldReturnFollowUpGuidance() {
            AnswerAnalysisResult result = tool.analyze("微服务架构优缺点？",
                    "我认为微服务架构的核心优势在于独立部署...", "PRESSURE");
            assertNotNull(result.getGuidance());
            assertNotNull(result.getFollowUpGuidance());
            assertTrue(result.getFollowUpGuidance().contains("压力追问指导"));
            assertTrue(result.getFollowUpGuidance().contains("边界条件"));
            assertTrue(result.getFollowUpGuidance().contains("替代方案"));
            assertTrue(result.getFollowUpGuidance().contains("具体实现"));
        }
        @Test
        @DisplayName("非 PRESSURE → followUpGuidance null")
        void nonPressure_shouldNullFollowUp() {
            AnswerAnalysisResult result = tool.analyze("什么是Java？",
                    "Java是面向对象的编程语言...", "TECH_DEEP");
            assertNotNull(result.getGuidance());
            assertNull(result.getFollowUpGuidance());
        }
        @Test
        @DisplayName("无 mode → followUpGuidance null")
        void noMode_shouldNullFollowUp() {
            AnswerAnalysisResult result = tool.analyze("Spring框架核心？",
                    "Spring框架提供了IOC和AOP两大核心功能...", null);
            assertNotNull(result.getGuidance());
            assertNull(result.getFollowUpGuidance());
        }
    }
}
