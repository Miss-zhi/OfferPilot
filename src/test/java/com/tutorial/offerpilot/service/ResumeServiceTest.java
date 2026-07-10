/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.tool.ResumeEvaluateResult;
import com.tutorial.offerpilot.dto.tool.ResumeParseResult;
import com.tutorial.offerpilot.service.ingestion.DocumentParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeService 单元测试")
class ResumeServiceTest {

    @Mock
    private DocumentParser documentParser;

    @InjectMocks
    private ResumeService resumeService;

    // ==================== parseResume ====================

    @Nested
    @DisplayName("parseResume")
    class ParseResumeTests {

        @Test
        @DisplayName("null URL → 返回空结果")
        void parseResume_nullUrl_shouldReturnEmptyResult() {
            ResumeParseResult result = resumeService.parseResume(null);

            assertNotNull(result);
            assertEquals("", result.getName());
            assertEquals("", result.getEmail());
            assertEquals("", result.getPhone());
            assertTrue(result.getEducation().isEmpty());
            assertTrue(result.getProjects().isEmpty());
            assertTrue(result.getSkills().isEmpty());
            assertTrue(result.getExperience().isEmpty());
            verifyNoInteractions(documentParser);
        }

        @Test
        @DisplayName("blank URL → 返回空结果")
        void parseResume_blankUrl_shouldReturnEmptyResult() {
            ResumeParseResult result = resumeService.parseResume("   ");

            assertNotNull(result);
            assertEquals("", result.getName());
            assertEquals("", result.getEmail());
            verifyNoInteractions(documentParser);
        }

        @Test
        @DisplayName("远程 http URL → 降级为文本模式，不调用 DocumentParser")
        void parseResume_remoteHttpUrl_shouldFallbackToTextMode() {
            ResumeParseResult result = resumeService.parseResume("http://example.com/resume.pdf");

            assertNotNull(result);
            assertEquals("", result.getName()); // no name in text-mode output
            verifyNoInteractions(documentParser);
        }

        @Test
        @DisplayName("远程 https URL → 降级为文本模式")
        void parseResume_remoteHttpsUrl_shouldFallbackToTextMode() {
            ResumeParseResult result = resumeService.parseResume("https://cdn.example.com/doc.docx");

            assertNotNull(result);
            verifyNoInteractions(documentParser);
        }

        @Test
        @DisplayName("本地文件解析成功 → 返回完整结构化结果")
        void parseResume_localFileSuccess_shouldReturnStructuredResult() throws IOException {
            String resumeText = """
                    张三

                    邮箱: zhangsan@example.com
                    电话: 13800138000

                    教育背景
                    北京大学 本科 计算机科学

                    项目经历
                    电商平台项目 负责后端开发

                    技能
                    Java Python Spring MySQL Redis Docker

                    工作经历
                    阿里巴巴 担任高级工程师 负责微服务架构设计
                    """;
            when(documentParser.parse(eq("/tmp/resume.pdf"), eq("pdf"))).thenReturn(resumeText);

            try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
                filesMock.when(() -> Files.exists(Path.of("/tmp/resume.pdf"))).thenReturn(true);

                ResumeParseResult result = resumeService.parseResume("/tmp/resume.pdf");

                assertEquals("张三", result.getName());
                assertEquals("zhangsan@example.com", result.getEmail());
                assertEquals("13800138000", result.getPhone());

                assertFalse(result.getEducation().isEmpty());
                assertTrue(result.getEducation().stream().anyMatch(e -> e.contains("北京大学")));

                assertFalse(result.getProjects().isEmpty());
                assertTrue(result.getProjects().stream().anyMatch(p -> p.contains("电商平台")));

                assertFalse(result.getSkills().isEmpty());
                assertTrue(result.getSkills().stream().anyMatch(s -> s.contains("Java")),
                        "skills should contain Java: " + result.getSkills());
                assertTrue(result.getSkills().stream().anyMatch(s -> s.contains("Spring")),
                        "skills should contain Spring: " + result.getSkills());

                assertFalse(result.getExperience().isEmpty());
            }

            verify(documentParser).parse("/tmp/resume.pdf", "pdf");
        }

        @Test
        @DisplayName("本地文件不存在 → 降级为文本模式")
        void parseResume_fileNotFound_shouldFallbackToTextMode() {
            try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);

                ResumeParseResult result = resumeService.parseResume("/nonexistent/resume.pdf");

                assertNotNull(result);
                assertEquals("", result.getName());
            }

            verifyNoInteractions(documentParser);
        }

        @Test
        @DisplayName("DocumentParser 抛 IOException → 降级为文本模式")
        void parseResume_documentParserThrowsIOE_shouldFallbackToTextMode() throws IOException {
            when(documentParser.parse(anyString(), anyString()))
                    .thenThrow(new IOException("Parse failed"));

            try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);

                ResumeParseResult result = resumeService.parseResume("/tmp/broken.pdf");

                assertNotNull(result);
                assertEquals("", result.getName());
            }

            verify(documentParser).parse(anyString(), anyString());
        }
    }

    // ==================== evaluateResume ====================

    @Nested
    @DisplayName("evaluateResume")
    class EvaluateResumeTests {

        @Test
        @DisplayName("null 文本 → 返回提示指导 + null 评分")
        void evaluateResume_nullText_shouldReturnPromptGuidance() {
            ResumeEvaluateResult result = resumeService.evaluateResume(null);

            assertNotNull(result);
            assertNull(result.getResumeText());
            assertEquals("请上传简历文件以进行评估。", result.getGuidance());
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("blank 文本 → 返回提示指导 + null 评分")
        void evaluateResume_blankText_shouldReturnPromptGuidance() {
            ResumeEvaluateResult result = resumeService.evaluateResume("\n  \t");

            assertNull(result.getResumeText());
            assertEquals("请上传简历文件以进行评估。", result.getGuidance());
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("短简历 → 返回原文 + 评估指导模板，评分字段为 null")
        void evaluateResume_shortText_shouldReturnResumeTextAndGuidance() {
            String shortResume = "张三，Java开发，有项目经验。";

            ResumeEvaluateResult result = resumeService.evaluateResume(shortResume);

            assertNotNull(result);
            assertEquals(shortResume, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertFalse(result.getGuidance().isBlank());
            assertTrue(result.getGuidance().contains("请评估以下简历的质量"));
            assertTrue(result.getGuidance().contains("综合评分（0-100）"));
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("中等简历 → 指导包含评估维度说明")
        void evaluateResume_mediumText_shouldContainEvaluationDimensions() {
            String mediumResume = buildResumeText(300, true, false, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(mediumResume);

            assertNotNull(result);
            assertEquals(mediumResume, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("内容完整性"));
            assertTrue(result.getGuidance().contains("表达质量"));
            assertTrue(result.getGuidance().contains("岗位匹配度"));
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
        }

        @Test
        @DisplayName("长简历 → 指导包含原文 + 输出格式说明")
        void evaluateResume_longText_shouldContainOutputFormat() {
            String longResume = buildResumeText(600, true, true, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(longResume);

            assertEquals(longResume, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("综合评分："));
            assertTrue(result.getGuidance().contains("优点："));
            assertTrue(result.getGuidance().contains("不足："));
            assertTrue(result.getGuidance().contains("改进建议："));
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("超长简历 → LLM 字段全部为 null/空，仅返回原文+指导")
        void evaluateResume_veryLongText_shouldOnlyReturnTextAndGuidance() {
            String fullResume = buildResumeText(1200, true, true, true, true);

            ResumeEvaluateResult result = resumeService.evaluateResume(fullResume);

            assertEquals(fullResume, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertFalse(result.getGuidance().isBlank());
            // LLM 字段：全部未填充
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("缺项目+缺技能 → 指导仍正常生成，LLM 字段为空")
        void evaluateResume_missingProjectAndSkills_shouldStillGenerateGuidance() {
            String text = buildResumeText(400, false, false, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(text, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("请评估以下简历的质量"));
            // LLM 字段全部为空 — 由 LLM 根据指导文本自行判断
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("含教育+公司 → 指导包含原文，评分/summary 为 null")
        void evaluateResume_withEducationAndCompany_shouldReturnGuidanceWithText() {
            String text = buildResumeText(300, true, true, true, true);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(text, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("张三"));
            assertTrue(result.getGuidance().contains("北京大学"));
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
        }

        @Test
        @DisplayName("含项目+技能 → 指导包含原文，LLM 字段为 null")
        void evaluateResume_withProjectAndSkills_shouldReturnTextOnly() {
            String text = buildResumeText(300, true, true, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(text, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("电商平台"));
            assertTrue(result.getGuidance().contains("Java"));
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getWeaknesses().isEmpty());
        }

        @Test
        @DisplayName("仅含项目无技能 → 指导仍正常生成")
        void evaluateResume_withOnlyProject_shouldStillReturnGuidance() {
            String text = buildResumeText(300, true, false, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(text, result.getResumeText());
            assertNotNull(result.getGuidance());
            assertFalse(result.getGuidance().isBlank());
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getSuggestions().isEmpty());
        }

        @Test
        @DisplayName("超长文本 → 所有 LLM 字段保持 null/空")
        void evaluateResume_maxLength_shouldNotPopulateLlmFields() {
            String text = buildResumeText(2000, true, true, true, true);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(text, result.getResumeText());
            assertNotNull(result.getGuidance());
            // 确认没有任何硬编码评分渗透
            assertNull(result.getOverallScore());
            assertNull(result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().isEmpty());
            assertTrue(result.getSuggestions().isEmpty());
        }
    }

    // ---- 辅助方法 ----

    /**
     * 构造指定长度和关键词的简历文本。
     */
    private static String buildResumeText(int targetLength, boolean hasProject,
                                          boolean hasSkill, boolean hasEducation,
                                          boolean hasCompany) {
        StringBuilder sb = new StringBuilder();
        sb.append("张三\n");
        sb.append("求职意向：Java开发工程师\n\n");

        if (hasEducation) {
            sb.append("教育背景\n");
            sb.append("北京大学 计算机科学与技术 本科 学历\n\n");
        }

        if (hasSkill) {
            sb.append("技能专长\n");
            sb.append("Java, Spring Boot, MySQL, Redis, Docker, 微服务, 分布式\n\n");
        }

        if (hasProject) {
            sb.append("项目经历\n");
            sb.append("电商平台项目 - 负责后端架构设计与核心模块开发\n\n");
        }

        if (hasCompany) {
            sb.append("工作经历\n");
            sb.append("阿里巴巴 公司 - 担任高级工程师，负责微服务治理\n\n");
        }

        // 填充至目标长度
        int padding = targetLength - sb.length();
        if (padding > 0) {
            sb.append("自我评价\n");
            sb.append("x".repeat(Math.max(0, padding)));
        }

        return sb.toString();
    }
}
