/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.ConfidenceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfidenceTool 单元测试")
class ConfidenceToolTest {

    private final ConfidenceTool tool = new ConfidenceTool();

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("analyze - 边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("text 为 null → 返回空分析")
        void nullText_shouldReturnEmptyResult() {
            ConfidenceResult result = tool.analyze(null);

            assertEquals(0, result.getConfidenceScore());
            assertEquals(0, result.getFillerDensity());
            assertEquals(0, result.getCorrectionCount());
            assertTrue(result.getFillerCounts().isEmpty());
            assertTrue(result.getGuidance().contains("文本为空"));
        }

        @Test
        @DisplayName("text 为空字符串 → 返回空分析")
        void blankText_shouldReturnEmptyResult() {
            ConfidenceResult result = tool.analyze("   ");

            assertEquals(0, result.getConfidenceScore());
            assertEquals(0, result.getFillerDensity());
            assertEquals(0, result.getCorrectionCount());
            assertTrue(result.getFillerCounts().isEmpty());
        }
    }

    // ==================== 口头禅检测 ====================

    @Nested
    @DisplayName("analyze - 口头禅检测")
    class FillerDetectionTests {

        @Test
        @DisplayName("无口头禅 → density=0, score 接近 100")
        void noFillers_shouldHighConfidence() {
            String text = "我认为面向对象编程是Java的核心思想，它通过封装继承多态实现了代码复用。Java在企业级开发中被广泛使用，尤其是在Spring生态中。";

            ConfidenceResult result = tool.analyze(text);

            assertEquals(0, result.getFillerCounts().size());
            assertEquals(0.0, result.getFillerDensity());
            assertTrue(result.getConfidenceScore() >= 70,
                    "无口头禅应有较高自信度，actual=" + result.getConfidenceScore());
        }

        @Test
        @DisplayName("大量口头禅 → fillerCounts 非空, density > 0")
        void manyFillers_shouldDetect() {
            String text = "嗯 那个 就是 Java 然后 那个 面向对象 就是 嗯 嗯 然后 封装继承 那个 多态";

            ConfidenceResult result = tool.analyze(text);

            assertFalse(result.getFillerCounts().isEmpty());
            assertTrue(result.getFillerDensity() > 0);
            assertTrue(result.getConfidenceScore() < 100,
                    "有口头禅应降低自信度，actual=" + result.getConfidenceScore());
        }

        @Test
        @DisplayName("高频\"嗯\" → 正确统计出现次数")
        void repeatedWord_shouldCountCorrectly() {
            String text = "嗯嗯嗯嗯嗯嗯嗯嗯嗯嗯";  // 10 个 "嗯"

            ConfidenceResult result = tool.analyze(text);

            assertTrue(result.getFillerCounts().containsKey("嗯"));
            assertEquals(10, result.getFillerCounts().get("嗯"));
        }
    }

    // ==================== 自我修正检测 ====================

    @Nested
    @DisplayName("analyze - 自我修正检测")
    class SelfCorrectionTests {

        @Test
        @DisplayName("无自我修正 → correctionCount=0")
        void noSelfCorrection_shouldReturnZero() {
            String text = "Java是面向对象语言，支持封装继承多态三大特性。Spring框架基于IoC和AOP。";

            ConfidenceResult result = tool.analyze(text);

            assertEquals(0, result.getCorrectionCount());
        }

        @Test
        @DisplayName("包含\"我觉得\"修正 → 检测到 correctionCount > 0")
        void selfCorrection_shouldBeDetected() {
            String text = "我觉得Java，不对，实际上Java的运行机制是先将源代码编译为字节码。";

            ConfidenceResult result = tool.analyze(text);

            assertTrue(result.getCorrectionCount() > 0);
        }

        @Test
        @DisplayName("多次修正 → correctionCount 正确统计")
        void multipleCorrections_shouldCountCorrectly() {
            String text = "我觉得 可能 也许 大概 其实 似乎 好像";

            ConfidenceResult result = tool.analyze(text);

            assertTrue(result.getCorrectionCount() >= 5,
                    "应检测到多个修正词，actual=" + result.getCorrectionCount());
        }
    }

    // ==================== 评分计算 ====================

    @Nested
    @DisplayName("analyze - 评分计算")
    class ScoringTests {

        @Test
        @DisplayName("完美回答 → 自信度评分应为 100 或接近 100")
        void perfectAnswer_shouldHaveHighScore() {
            String text = ("Java语言特性包括面向对象、跨平台、自动内存管理等。"
                    + "Spring框架提供了依赖注入和面向切面编程的支持。"
                    + "MySQL数据库使用B+树索引优化查询性能。").repeat(4);

            ConfidenceResult result = tool.analyze(text);

            assertTrue(result.getConfidenceScore() >= 80,
                    "流畅回答应得分 >= 80，actual=" + result.getConfidenceScore());
        }

        @Test
        @DisplayName("极短回答（<50字） → 降信度扣分")
        void veryShortAnswer_shouldBePenalized() {
            String text = "Java";

            ConfidenceResult result = tool.analyze(text);

            // 样本不足应额外扣 20 分
            assertTrue(result.getConfidenceScore() <= 80,
                    "样本不足应扣分，actual=" + result.getConfidenceScore());
        }

        @Test
        @DisplayName("大量口头禅 + 修正 → 自信度评分偏低")
        void manyFillersAndCorrections_shouldLowScore() {
            String text = ("嗯 那个 我觉得Java是，不对，应该是实际上Java的嗯核心就是那个面向对象。"
                    + "嗯 好像 大概 也许 嗯 嗯 那个 然后 就是 Spring框架那个那个那个").repeat(3);

            ConfidenceResult result = tool.analyze(text);

            assertTrue(result.getConfidenceScore() < 70,
                    "大量口头禅应导致低自信度，actual=" + result.getConfidenceScore());
        }

        @Test
        @DisplayName("自信度评分范围为 0-100")
        void confidenceScore_shouldBeWithinRange() {
            for (String text : new String[]{"", "A", "A".repeat(100), "嗯".repeat(1000)}) {
                ConfidenceResult result = tool.analyze(text);
                assertTrue(result.getConfidenceScore() >= 0 && result.getConfidenceScore() <= 100,
                        "Score out of range: " + result.getConfidenceScore() + " for text len=" + text.length());
            }
        }
    }

    // ==================== guidance 内容 ====================

    @Nested
    @DisplayName("analyze - guidance 内容")
    class GuidanceTests {

        @Test
        @DisplayName("guidance 应包含自信度评分和评估等级")
        void guidance_shouldContainScoreAndAssessment() {
            String text = "Java是面向对象语言。".repeat(10);

            ConfidenceResult result = tool.analyze(text);

            assertTrue(result.getGuidance().contains("自信度评分"));
            assertTrue(result.getGuidance().contains("/100"));
        }

        @Test
        @DisplayName("guidance 应包含口头禅密度")
        void guidance_shouldContainFillerDensity() {
            String text = "嗯 那个 Java 就是 嗯 面向对象";

            ConfidenceResult result = tool.analyze(text);

            assertTrue(result.getGuidance().contains("口头禅密度"));
        }
    }
}
