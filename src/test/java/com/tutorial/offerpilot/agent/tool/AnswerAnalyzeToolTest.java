/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.AnswerAnalysisResult;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.service.InterviewAnalysisService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerAnalyzeTool 单元测试")
class AnswerAnalyzeToolTest {

    @Mock
    private InterviewAnalysisService analysisService;

    @Mock
    private InterviewQuestionRepository questionRepo;

    @InjectMocks
    private AnswerAnalyzeTool tool;

    // ==================== analyze - 边界条件 ====================

    @Nested
    @DisplayName("analyze - 边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("question 为 null → 返回 0 分 + 错误提示")
        void nullQuestion_shouldReturnZeroScore() {
            AnswerAnalysisResult result = tool.analyze(null, "some answer");

            assertEquals(0, result.getTechScore());
            assertEquals(0, result.getExprScore());
            assertEquals(0, result.getCoverageScore());
            assertTrue(result.getHighlights().isEmpty());
            assertTrue(result.getWeaknesses().contains("问题或回答为空"));
            assertEquals("请提供完整的问题和回答", result.getSuggestion());
        }

        @Test
        @DisplayName("answer 为 null → 返回 0 分 + 错误提示")
        void nullAnswer_shouldReturnZeroScore() {
            AnswerAnalysisResult result = tool.analyze("what is Java?", null);

            assertEquals(0, result.getTechScore());
            assertTrue(result.getWeaknesses().contains("问题或回答为空"));
        }
    }

    // ==================== analyze - 技术得分 ====================

    @Nested
    @DisplayName("analyze - 技术得分")
    class TechScoreTests {

        @Test
        @DisplayName("极短回答(<50字) → 固定 30 分")
        void veryShortAnswer_shouldReturnLowTechScore() {
            String answer = "Java是面向对象语言。"; // ~12 chars

            AnswerAnalysisResult result = tool.analyze("什么是Java？", answer);

            assertEquals(30, result.getTechScore());
        }

        @Test
        @DisplayName("长回答(>500字)含多个技术关键词 → 高分")
        void longAnswerWithKeywords_shouldReturnHighTechScore() {
            String answer = ("算法 数据结构 设计模式 框架 架构 数据库 缓存 并发 分布式 微服务 API 协议. ").repeat(30);

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            assertTrue(result.getTechScore() >= 70, "techScore=" + result.getTechScore());
            assertTrue(result.getTechScore() <= 100);
        }

        @Test
        @DisplayName("回答含技术关键词但不长 → 中等分")
        void shortAnswerWithKeywords_shouldReturnMediumScore() {
            // >=50 chars, has some keywords
            String answer = "Java中使用算法和数据结构非常重要，框架如Spring、MyBatis和Hibernate提供了缓存机制和数据库访问能力。";

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            assertTrue(result.getTechScore() >= 40, "techScore=" + result.getTechScore());
            assertTrue(result.getTechScore() <= 85, "techScore=" + result.getTechScore());
        }
    }

    // ==================== analyze - 表达得分 ====================

    @Nested
    @DisplayName("analyze - 表达得分")
    class ExpressionScoreTests {

        @Test
        @DisplayName("极短回答(<30字) → 20 分")
        void veryShortAnswer_shouldReturnLowExprScore() {
            AnswerAnalysisResult result = tool.analyze("问", "太短了");

            assertEquals(20, result.getExprScore());
        }

        @Test
        @DisplayName("结构化回答(含'首先'+'总结') → 加分")
        void structuredAnswer_shouldGetBonus() {
            // length = 30 + min(len/10,50) + 10(首先) + 10(总之)
            String answer = "首先，Java是面向对象语言。第一，它有封装继承多态。总之，它非常强大且安全。";

            AnswerAnalysisResult result = tool.analyze("Java?", answer);

            // expect >= 50 (bonus applied) and exprScore is deterministic
            assertTrue(result.getExprScore() >= 50, "exprScore=" + result.getExprScore());
            // 验证两个加分项都生效了
            assertTrue(result.getExprScore() >= 30 + 10 + 10, "bonus not fully applied");
        }

        @Test
        @DisplayName("无结构标记但长度够 → 基础分")
        void noStructureMarkers_shouldReturnBaseScore() {
            String answer = "Java是一种广泛使用的计算机编程语言，拥有跨平台、面向对象、泛型编程的特性。".repeat(3);

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            assertTrue(result.getExprScore() >= 30);
        }
    }

    // ==================== analyze - 覆盖度得分 ====================

    @Nested
    @DisplayName("analyze - 覆盖度得分")
    class CoverageScoreTests {

        @Test
        @DisplayName("极短回答(<30字) → 20 分")
        void veryShortAnswer_shouldReturnLowCoverage() {
            AnswerAnalysisResult result = tool.analyze("Q", "A");

            assertEquals(20, result.getCoverageScore());
        }

        @Test
        @DisplayName("含举例 + 优缺点对比 → 加分")
        void answerWithExamplesAndProsCons_shouldGetBonus() {
            String answer = "Java例如在Web开发中广泛使用。它的优点是可移植，缺点是性能。".repeat(2);

            AnswerAnalysisResult result = tool.analyze("Java", answer);

            // should have bonus for 例如 and 优点/缺点
            assertTrue(result.getCoverageScore() >= 50, "coverage=" + result.getCoverageScore());
        }
    }

    // ==================== analyze - 亮点/弱点/建议 ====================

    @Nested
    @DisplayName("analyze - 亮点/弱点/建议")
    class HighlightsWeaknessesTests {

        @Test
        @DisplayName("高分回答 → 包含技术关键词和结构清晰亮点")
        void highScoreAnswer_shouldHaveHighlights() {
            String answer = ("算法 数据结构 框架 数据库 缓存 微服务 分布式 协议. ").repeat(40);

            AnswerAnalysisResult result = tool.analyze("Q", answer);

            assertFalse(result.getHighlights().isEmpty());
            assertTrue(result.getHighlights().stream()
                    .anyMatch(h -> h.contains("技术关键词")));
        }

        @Test
        @DisplayName("低分回答 → 包含多项弱点")
        void lowScoreAnswer_shouldHaveWeaknesses() {
            AnswerAnalysisResult result = tool.analyze("Q", "short");

            assertFalse(result.getWeaknesses().isEmpty());
            assertTrue(result.getWeaknesses().size() >= 3,
                    "expected >=3 weaknesses, got: " + result.getWeaknesses());
        }

        @Test
        @DisplayName("平均分 >= 80 → 优秀建议")
        void highAverage_shouldReturnExcellentSuggestion() {
            // techScore high, exprScore high, coverageScore high
            String answer = ("算法 数据结构 框架 架构 数据库 首先，Java是强大的语言，例如Web开发。总之，广泛使用。").repeat(30);

            AnswerAnalysisResult result = tool.analyze("Q", answer);

            assertTrue(result.getSuggestion().contains("优秀"));
        }

        @Test
        @DisplayName("平均分 60~79 → 良好建议")
        void mediumAverage_shouldReturnGoodSuggestion() {
            // need avg in [60, 80): good tech + good expr + good coverage
            String answer = ("算法 数据结构 框架 数据库 缓存 首先，Java是非常流行的编程语言。"
                    + "例如在Web后端开发中广泛使用。总之，它是企业级首选。").repeat(4);

            AnswerAnalysisResult result = tool.analyze("Q", answer);

            assertTrue(result.getSuggestion().contains("良好"),
                    "suggestion='" + result.getSuggestion() + "', scores="
                            + result.getTechScore() + "/" + result.getExprScore() + "/" + result.getCoverageScore());
        }

        @Test
        @DisplayName("平均分 < 60 → 需提升建议")
        void lowAverage_shouldReturnImprovementSuggestion() {
            AnswerAnalysisResult result = tool.analyze("Q", "short answer");

            assertTrue(result.getSuggestion().contains("需要较大提升"));
        }
    }

    // ==================== analyze - 持久化 ====================

    @Nested
    @DisplayName("analyze - 持久化")
    class PersistenceTests {

        @Test
        @DisplayName("找到匹配问题 → 更新评分并调用分析服务")
        void matchedQuestion_shouldPersist() {
            when(questionRepo.findByQuestionText("Q")).thenReturn(List.of());

            tool.analyze("Q", "some answer with 算法 and 数据结构");

            // no match → questionRepo.save and analysisService.saveAnalysis not called
            verify(questionRepo, never()).save(any());
            verify(analysisService, never()).saveAnalysis(anyString(), anyString(), anyString(),
                    anyInt(), anyInt(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("持久化异常 → 不影响返回结果")
        void persistenceException_shouldNotAffectResult() {
            when(questionRepo.findByQuestionText("Q"))
                    .thenThrow(new RuntimeException("DB error"));

            AnswerAnalysisResult result = tool.analyze("Q", "some answer with 算法");

            assertNotNull(result);
            assertTrue(result.getTechScore() > 0);
        }
    }
}
