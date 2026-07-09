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
                assertTrue(result.getSkills().contains("Java"));
                assertTrue(result.getSkills().contains("Spring"));

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
        @DisplayName("null 文本 → 返回 0 分 + 空简历提示")
        void evaluateResume_nullText_shouldReturnZeroScore() {
            ResumeEvaluateResult result = resumeService.evaluateResume(null);

            assertNotNull(result);
            assertEquals(0, result.getOverallScore());
            assertEquals("简历内容为空", result.getSummary());
            assertTrue(result.getStrengths().isEmpty());
            assertTrue(result.getWeaknesses().contains("请提供简历文本"));
            assertTrue(result.getSuggestions().contains("上传简历文件进行评估"));
        }

        @Test
        @DisplayName("blank 文本 → 返回 0 分 + 空简历提示")
        void evaluateResume_blankText_shouldReturnZeroScore() {
            ResumeEvaluateResult result = resumeService.evaluateResume("\n  \t");

            assertEquals(0, result.getOverallScore());
            assertEquals("简历内容为空", result.getSummary());
        }

        @Test
        @DisplayName("极短简历(<200字) → 低分 + 内容过短弱点")
        void evaluateResume_shortText_shouldReturnLowScore() {
            String shortResume = "张三，Java开发，有项目经验。"; // ~15 chars

            ResumeEvaluateResult result = resumeService.evaluateResume(shortResume);

            assertNotNull(result);
            assertTrue(result.getOverallScore() <= 50,
                    "Expected <=50 but was " + result.getOverallScore());
            assertTrue(result.getWeaknesses().contains("简历内容过短，建议补充更多细节"));
            assertTrue(result.getWeaknesses().contains("缺少技能列表，不利于ATS系统筛选"));
        }

        @Test
        @DisplayName("中等简历(200-500字)含项目 → 中等分数")
        void evaluateResume_mediumText_shouldReturnMediumScore() {
            String mediumResume = buildResumeText(300, true, false, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(mediumResume);

            assertNotNull(result);
            assertTrue(result.getOverallScore() >= 40, "score=" + result.getOverallScore());
            assertTrue(result.getOverallScore() <= 70, "score=" + result.getOverallScore());
            assertTrue(result.getStrengths().contains("包含项目经验描述"));
        }

        @Test
        @DisplayName("长简历(500-1000字)含项目+技能 → 良好分数")
        void evaluateResume_longText_shouldReturnGoodScore() {
            String longResume = buildResumeText(600, true, true, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(longResume);

            assertTrue(result.getOverallScore() >= 50, "score=" + result.getOverallScore());
            assertTrue(result.getStrengths().contains("简历内容充实，信息量充足"));
            assertTrue(result.getStrengths().contains("包含项目经验描述"));
            assertTrue(result.getSummary().contains("良好"));
        }

        @Test
        @DisplayName("超长简历(>1000字)含全部关键词 → 接近满分但不超100")
        void evaluateResume_veryLongText_shouldCapAt100() {
            String fullResume = buildResumeText(1200, true, true, true, true);

            ResumeEvaluateResult result = resumeService.evaluateResume(fullResume);

            assertTrue(result.getOverallScore() >= 80, "score=" + result.getOverallScore());
            assertTrue(result.getOverallScore() <= 100, "score exceeded 100: " + result.getOverallScore());
            assertTrue(result.getSummary().contains("优秀"));
            assertTrue(result.getStrengths().contains("包含工作经历"));
        }

        @Test
        @DisplayName("缺项目+缺技能 → 对应弱点 + 对应建议")
        void evaluateResume_missingProjectAndSkills_shouldReportWeaknesses() {
            String text = buildResumeText(400, false, false, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertTrue(result.getWeaknesses().contains("缺少项目经验描述"));
            assertTrue(result.getWeaknesses().contains("缺少技能列表，不利于ATS系统筛选"));

            // 建议应与弱点匹配
            assertTrue(result.getSuggestions().stream()
                    .anyMatch(s -> s.contains("添加2-3个核心项目")));
            assertTrue(result.getSuggestions().stream()
                    .anyMatch(s -> s.contains("技能专长板块")));
        }

        @Test
        @DisplayName("评分边界值 80 → 优秀摘要")
        void evaluateResume_score80_shouldBeExcellent() {
            // 构造一个恰好能得 80 分的文本
            // 基础30 + len>200(+10) + len>500(+10) [=50] + 项目负责(+10) + 技能技术(+10) + 大学学历(+10) + 公司工作(+10) = 90
            // 要得80: 30 + 10(len>200) + 10(项目负责) + 10(技能) + 10(大学) + 10(公司)
            // = 80, len=300
            String text = buildResumeText(300, true, true, true, true);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(80, result.getOverallScore(), "unexpected score");
            assertTrue(result.getSummary().contains("优秀"));
        }

        @Test
        @DisplayName("评分边界值 60 → 良好摘要")
        void evaluateResume_score60_shouldBeGood() {
            // 30 + 10(len>200) + 10(项目负责) + 10(技能) = 60, len=300
            String text = buildResumeText(300, true, true, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(60, result.getOverallScore(), "unexpected score");
            assertTrue(result.getSummary().contains("良好"));
        }

        @Test
        @DisplayName("评分边界值 59 → 需改进摘要")
        void evaluateResume_scoreBelow60_shouldNeedImprovement() {
            // 30 + 10(len>200) + 10(项目负责) = 50, len=300, no skill, no education, no company
            String text = buildResumeText(300, true, false, false, false);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertEquals(50, result.getOverallScore(), "unexpected score");
            assertTrue(result.getSummary().contains("需要较大改进"));
        }

        @Test
        @DisplayName("分数不会超过 100")
        void evaluateResume_scoreNeverExceeds100() {
            // 即使所有条件满足，总分也不应超过 100
            String text = buildResumeText(2000, true, true, true, true);

            ResumeEvaluateResult result = resumeService.evaluateResume(text);

            assertTrue(result.getOverallScore() <= 100,
                    "Score " + result.getOverallScore() + " should not exceed 100");
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
