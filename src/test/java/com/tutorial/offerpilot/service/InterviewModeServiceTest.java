/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterviewModeService 单元测试")
class InterviewModeServiceTest {

    private final InterviewModeService service = new InterviewModeService();

    // ==================== getPhaseSequence ====================

    @Nested
    @DisplayName("getPhaseSequence - 模式阶段序列")
    class PhaseSequenceTests {

        @Test
        @DisplayName("TECH_DEEP → 返回 12 个阶段，包含底层原理")
        void techDeep_shouldReturn12Phases() {
            List<String> phases = service.getPhaseSequence("TECH_DEEP");

            assertEquals(12, phases.size());
            assertEquals("自我介绍", phases.get(0));
            assertTrue(phases.contains("底层原理"));
            assertTrue(phases.contains("架构设计"));
            assertTrue(phases.contains("职业规划"));
        }

        @Test
        @DisplayName("BEHAVIOR → 返回 12 个阶段，包含行为面试")
        void behavior_shouldReturn12Phases() {
            List<String> phases = service.getPhaseSequence("BEHAVIOR");

            assertEquals(12, phases.size());
            assertEquals("自我介绍", phases.get(0));
            assertTrue(phases.contains("行为面试"));
            assertTrue(phases.contains("情景分析"));
            assertTrue(phases.contains("压力测试"));
        }

        @Test
        @DisplayName("SYSTEM_DESIGN → 返回 12 个阶段，包含系统设计")
        void systemDesign_shouldReturn12Phases() {
            List<String> phases = service.getPhaseSequence("SYSTEM_DESIGN");

            assertEquals(12, phases.size());
            assertEquals("自我介绍", phases.get(0));
            assertTrue(phases.contains("系统设计"));
            assertTrue(phases.contains("架构设计"));
            assertTrue(phases.contains("性能优化"));
        }

        @Test
        @DisplayName("PRESSURE → 返回 12 个阶段，包含压力测试")
        void pressure_shouldReturn12Phases() {
            List<String> phases = service.getPhaseSequence("PRESSURE");

            assertEquals(12, phases.size());
            assertEquals("自我介绍", phases.get(0));
            assertTrue(phases.contains("压力测试"));
            assertTrue(phases.contains("行为面试"));
        }

        @Test
        @DisplayName("null mode → 返回默认序列")
        void nullMode_shouldReturnDefault() {
            List<String> phases = service.getPhaseSequence(null);

            assertEquals(12, phases.size());
            assertEquals("自我介绍", phases.get(0));
        }

        @Test
        @DisplayName("未知 mode → 返回默认序列")
        void unknownMode_shouldReturnDefault() {
            List<String> phases = service.getPhaseSequence("UNKNOWN_MODE");

            assertEquals(12, phases.size());
            assertEquals("自我介绍", phases.get(0));
        }
    }

    // ==================== determineDifficulty ====================

    @Nested
    @DisplayName("determineDifficulty - 模式感知难度")
    class DifficultyTests {

        @Test
        @DisplayName("PRESSURE 模式 → 均分 >= 60 返回 hard")
        void pressureHighScore_shouldReturnHard() {
            assertEquals("hard", service.determineDifficulty("PRESSURE", 70.0, 1));
        }

        @Test
        @DisplayName("PRESSURE 模式 → 第 4 题返回 hard（无论均分）")
        void pressureLateQuestion_shouldReturnHard() {
            assertEquals("hard", service.determineDifficulty("PRESSURE", null, 4));
        }

        @Test
        @DisplayName("PRESSURE 模式 → 第 1 题无均分返回 medium")
        void pressureEarlyQuestion_shouldReturnMedium() {
            assertEquals("medium", service.determineDifficulty("PRESSURE", null, 1));
        }

        @Test
        @DisplayName("PRESSURE 模式 → 均分 30 第 1 题返回 medium")
        void pressureLowScore_shouldReturnMedium() {
            assertEquals("medium", service.determineDifficulty("PRESSURE", 30.0, 1));
        }

        @Test
        @DisplayName("SYSTEM_DESIGN 模式 → 第 1 题 easy")
        void systemDesignFirst_shouldReturnEasy() {
            assertEquals("easy", service.determineDifficulty("SYSTEM_DESIGN", null, 1));
        }

        @Test
        @DisplayName("SYSTEM_DESIGN 模式 → 第 3 题 medium")
        void systemDesignThird_shouldReturnMedium() {
            assertEquals("medium", service.determineDifficulty("SYSTEM_DESIGN", null, 3));
        }

        @Test
        @DisplayName("SYSTEM_DESIGN 模式 → 第 6 题 hard")
        void systemDesignSixth_shouldReturnHard() {
            assertEquals("hard", service.determineDifficulty("SYSTEM_DESIGN", null, 6));
        }

        @Test
        @DisplayName("默认模式 → 均分 80 返回 hard")
        void defaultHighScore_shouldReturnHard() {
            assertEquals("hard", service.determineDifficulty("TECH_DEEP", 80.0, 1));
        }

        @Test
        @DisplayName("默认模式 → 均分 60 返回 medium")
        void defaultMediumScore_shouldReturnMedium() {
            assertEquals("medium", service.determineDifficulty("TECH_DEEP", 60.0, 1));
        }

        @Test
        @DisplayName("默认模式 → 均分 30 返回 easy")
        void defaultLowScore_shouldReturnEasy() {
            assertEquals("easy", service.determineDifficulty("TECH_DEEP", 30.0, 1));
        }

        @Test
        @DisplayName("默认模式 → 第 6 题无均分返回 hard")
        void defaultLateQuestion_shouldReturnHard() {
            assertEquals("hard", service.determineDifficulty("TECH_DEEP", null, 6));
        }

        @Test
        @DisplayName("默认模式 → 第 3 题无均分返回 medium")
        void defaultMidQuestion_shouldReturnMedium() {
            assertEquals("medium", service.determineDifficulty("TECH_DEEP", null, 3));
        }
    }

    // ==================== extractProjectKeywords ====================

    @Nested
    @DisplayName("extractProjectKeywords - 简历关键词提取")
    class ExtractKeywordsTests {

        @Test
        @DisplayName("含项目行 → 提取项目相关行")
        void resumeWithProjects_shouldExtract() {
            String resume = "个人信息：张三\n"
                    + "项目经验：电商秒杀系统，使用 Redis+MQ\n"
                    + "实习经历：参与数据中台建设\n"
                    + "教育背景：清华大学\n"
                    + "负责模块：支付网关\n";

            List<String> keywords = service.extractProjectKeywords(resume);

            assertFalse(keywords.isEmpty());
            assertTrue(keywords.stream().anyMatch(k -> k.contains("项目经验")));
            assertTrue(keywords.stream().anyMatch(k -> k.contains("实习经历")));
            assertTrue(keywords.stream().anyMatch(k -> k.contains("负责模块")));
            assertTrue(keywords.stream().noneMatch(k -> k.contains("个人信息")));
            assertTrue(keywords.stream().noneMatch(k -> k.contains("教育背景")));
        }

        @Test
        @DisplayName("空简历 → 返回空列表")
        void emptyResume_shouldReturnEmpty() {
            assertTrue(service.extractProjectKeywords(null).isEmpty());
            assertTrue(service.extractProjectKeywords("").isEmpty());
            assertTrue(service.extractProjectKeywords("   ").isEmpty());
        }

        @Test
        @DisplayName("无项目相关行 → 返回空列表")
        void noProjects_shouldReturnEmpty() {
            String resume = "个人信息：张三\n教育背景：清华大学\n";

            List<String> keywords = service.extractProjectKeywords(resume);

            assertTrue(keywords.isEmpty());
        }

        @Test
        @DisplayName("含开源相关行 → 提取")
        void resumeWithOpenSource_shouldExtract() {
            String resume = "开源贡献：参与 Apache Flink 社区\n";

            List<String> keywords = service.extractProjectKeywords(resume);

            assertFalse(keywords.isEmpty());
            assertTrue(keywords.get(0).contains("开源贡献"));
        }
    }
}
