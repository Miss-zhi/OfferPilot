/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.TimeAllocationResult;
import com.tutorial.offerpilot.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeAllocationTool 单元测试")
class TimeAllocationToolTest {

    private final TimeAllocationTool tool = new TimeAllocationTool();

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("analyze - 边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("answersJson 为 null → 返回空结果")
        void nullJson_shouldReturnEmptyResult() {
            TimeAllocationResult result = tool.analyze(null);

            assertEquals(0, result.getTotalSeconds());
            assertEquals(0, result.getTooShortCount());
            assertEquals(0, result.getTooLongCount());
            assertTrue(result.getItems().isEmpty());
            assertTrue(result.getGuidance().contains("回答数据为空"));
        }

        @Test
        @DisplayName("answersJson 为空字符串 → 返回空结果")
        void blankJson_shouldReturnEmptyResult() {
            TimeAllocationResult result = tool.analyze("   ");

            assertEquals(0, result.getTotalSeconds());
            assertTrue(result.getItems().isEmpty());
        }

        @Test
        @DisplayName("无效 JSON → 抛出 BusinessException")
        void invalidJson_shouldThrowBusinessException() {
            assertThrows(BusinessException.class,
                    () -> tool.analyze("not valid json"));
        }
    }

    // ==================== 时长估算 ====================

    @Nested
    @DisplayName("analyze - 时长估算")
    class DurationEstimationTests {

        @Test
        @DisplayName("短回答 → 评估为 TOO_SHORT")
        void shortAnswer_shouldBeTooShort() {
            String json = "[{\"question\":\"Q1\",\"answer\":\"短\"}]";

            TimeAllocationResult result = tool.analyze(json);

            assertEquals(1, result.getItems().size());
            assertEquals("TOO_SHORT", result.getItems().get(0).getAssessment());
            assertEquals(1, result.getTooShortCount());
            assertEquals(0, result.getTooLongCount());
        }

        @Test
        @DisplayName("适中回答（约250字，60秒） → 评估为 GOOD")
        void idealAnswer_shouldBeGood() {
            // 250字 = 250 chars/min → 60秒
            String answer = "A".repeat(250);
            String json = "[{\"question\":\"Q1\",\"answer\":\"" + answer + "\"}]";

            TimeAllocationResult result = tool.analyze(json);

            assertEquals("GOOD", result.getItems().get(0).getAssessment());
            assertEquals(60, result.getItems().get(0).getEstimatedSeconds());
        }

        @Test
        @DisplayName("中等回答（约150字） → 评估为 ACCEPTABLE")
        void mediumAnswer_shouldBeAcceptable() {
            // 150字 = 150*60/250 = 36秒
            String answer = "A".repeat(150);
            String json = "[{\"question\":\"Q1\",\"answer\":\"" + answer + "\"}]";

            TimeAllocationResult result = tool.analyze(json);

            assertEquals("ACCEPTABLE", result.getItems().get(0).getAssessment());
        }

        @Test
        @DisplayName("长回答（约800字） → 评估为 TOO_LONG")
        void longAnswer_shouldBeTooLong() {
            // 800字 = 800*60/250 = 192秒
            String answer = "A".repeat(800);
            String json = "[{\"question\":\"Q1\",\"answer\":\"" + answer + "\"}]";

            TimeAllocationResult result = tool.analyze(json);

            assertEquals("TOO_LONG", result.getItems().get(0).getAssessment());
            assertEquals(1, result.getTooLongCount());
        }
    }

    // ==================== 多题场景 ====================

    @Nested
    @DisplayName("analyze - 多题场景")
    class MultiQuestionTests {

        @Test
        @DisplayName("多题混合 → 正确统计各评估数量")
        void mixedQuestions_shouldCountCorrectly() {
            String shortAns = "A".repeat(20);   // 约5秒 → TOO_SHORT
            String goodAns = "A".repeat(250);   // 约60秒 → GOOD
            String longAns = "A".repeat(800);   // 约192秒 → TOO_LONG
            String json = String.format(
                    "[{\"question\":\"Q1\",\"answer\":\"%s\"},"
                            + "{\"question\":\"Q2\",\"answer\":\"%s\"},"
                            + "{\"question\":\"Q3\",\"answer\":\"%s\"}]",
                    shortAns, goodAns, longAns);

            TimeAllocationResult result = tool.analyze(json);

            assertEquals(3, result.getItems().size());
            assertEquals(1, result.getTooShortCount());
            assertEquals(1, result.getTooLongCount());
            assertTrue(result.getTotalSeconds() > 0);
            assertTrue(result.getGuidance().contains("总计"));
        }

        @Test
        @DisplayName("answers 数组为空 → 返回空统计")
        void emptyArray_shouldReturnEmptyStats() {
            String json = "[]";

            TimeAllocationResult result = tool.analyze(json);

            assertEquals(0, result.getItems().size());
            assertEquals(0, result.getTotalSeconds());
            assertEquals(0, result.getTooShortCount());
        }
    }

    // ==================== question 截断 ====================

    @Nested
    @DisplayName("analyze - question 截断")
    class TruncationTests {

        @Test
        @DisplayName("question 超过 50 字 → 截断至 50 字")
        void longQuestion_shouldTruncate() {
            String longQ = "Q".repeat(100);
            String answer = "A".repeat(250);
            String json = "[{\"question\":\"" + longQ + "\",\"answer\":\"" + answer + "\"}]";

            TimeAllocationResult result = tool.analyze(json);

            assertEquals(50, result.getItems().get(0).getQuestion().length());
        }
    }

    // ==================== guidance 内容 ====================

    @Nested
    @DisplayName("analyze - guidance 内容")
    class GuidanceTests {

        @Test
        @DisplayName("guidance 应包含评估标准说明")
        void guidance_shouldContainEvaluationCriteria() {
            String answer = "A".repeat(250);
            String json = "[{\"question\":\"Q1\",\"answer\":\"" + answer + "\"}]";

            TimeAllocationResult result = tool.analyze(json);

            assertTrue(result.getGuidance().contains("GOOD=时长适中"));
            assertTrue(result.getGuidance().contains("TOO_SHORT=回答过短"));
            assertTrue(result.getGuidance().contains("TOO_LONG=回答过长"));
        }
    }
}
