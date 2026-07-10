/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.dto.tool.ResumeEvaluateResult;
import com.tutorial.offerpilot.dto.tool.ResumeParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("ResumeService 集成测试")
class ResumeServiceIT extends AbstractServiceIT {

    @Autowired
    private ResumeService resumeService;

    private static final String SAMPLE_RESUME = """
            张三
            zhangsan@example.com
            13800138000

            教育背景
            北京大学 计算机科学与技术 本科 2018-2022

            工作经历
            字节跳动 后端开发工程师 2022-至今
            负责用户增长系统设计与开发

            项目经历
            电商推荐系统 — 使用 Spring Boot + Redis 实现实时推荐

            技能：Java、Python、Spring Boot、MySQL、Redis
            熟练掌握分布式系统设计
            """;

    // ==================== parseResume ====================

    @Nested
    @DisplayName("parseResume")
    class ParseTests {

        @Test
        @DisplayName("null URL → 返回空结果")
        void parseResume_nullUrl_shouldReturnEmpty() {
            ResumeParseResult result = resumeService.parseResume(null);

            assertEquals("", result.getName());
            assertTrue(result.getEducation().isEmpty());
        }

        @Test
        @DisplayName("空白 URL → 返回空结果")
        void parseResume_blankUrl_shouldReturnEmpty() {
            ResumeParseResult result = resumeService.parseResume("   ");

            assertEquals("", result.getName());
            assertTrue(result.getSkills().isEmpty());
        }
    }

    // ==================== evaluateResume ====================

    @Nested
    @DisplayName("evaluateResume")
    class EvaluateTests {

        @Test
        @DisplayName("有效简历文本 → 返回评估指导")
        void evaluateResume_withValidText_shouldReturnGuidance() {
            ResumeEvaluateResult result = resumeService.evaluateResume(SAMPLE_RESUME);

            assertNotNull(result.getResumeText());
            assertEquals(SAMPLE_RESUME, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("请评估以下简历的质量"));
            assertTrue(result.getGuidance().contains("综合评分"));
            assertTrue(result.getGuidance().contains("改进建议"));
            // LLM-generated fields should be null/empty
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("null 简历文本 → 返回提示信息")
        void evaluateResume_nullText_shouldReturnHint() {
            ResumeEvaluateResult result = resumeService.evaluateResume(null);

            assertNull(result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("请上传简历文件"));
            assertNull(result.getOverallScore());
        }

        @Test
        @DisplayName("空白简历文本 → 返回提示信息")
        void evaluateResume_blankText_shouldReturnHint() {
            ResumeEvaluateResult result = resumeService.evaluateResume("");

            assertNull(result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("请上传简历文件"));
        }

        @Test
        @DisplayName("guidance 包含评估维度")
        void evaluateResume_guidance_shouldContainDimensions() {
            ResumeEvaluateResult result = resumeService.evaluateResume(SAMPLE_RESUME);

            assertTrue(result.getGuidance().contains("内容完整性"));
            assertTrue(result.getGuidance().contains("表达质量"));
            assertTrue(result.getGuidance().contains("岗位匹配度"));
        }
    }
}
