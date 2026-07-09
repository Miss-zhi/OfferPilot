/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.entity.AnalysisReport;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.service.InterviewAnalysisService;
import com.tutorial.offerpilot.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportController Web 层测试")
class ReportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InterviewAnalysisService analysisService;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController controller;

    private static final String USER_ID = "testuser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        User user = new User(USER_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AnalysisReport buildReport(String reportId, int score) {
        AnalysisReport report = new AnalysisReport();
        report.setReportId(reportId);
        report.setUserId(USER_ID);
        report.setSessionId("sess-001");
        report.setReportType("INTERVIEW");
        report.setOverallScore(score);
        report.setDimensionsJson("{\"technical\":80,\"communication\":75}");
        report.setDetailsJson("{\"question1\":\"answer1\"}");
        report.setImprovementsJson("{\"tips\":[\"提高算法能力\"]}");
        return report;
    }

    // ==================== GET /api/v1/offerpilot/reports ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/reports")
    class ListReportsTests {

        @Test
        @DisplayName("正常查询 → 200 + 报告列表")
        void listReports_shouldReturn200() throws Exception {
            AnalysisReport r1 = buildReport("rpt-001", 85);
            AnalysisReport r2 = buildReport("rpt-002", 90);
            when(reportService.listReports(USER_ID)).thenReturn(List.of(r1, r2));

            mockMvc.perform(get("/api/v1/offerpilot/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].reportId").value("rpt-001"))
                    .andExpect(jsonPath("$.data[0].overallScore").value(85))
                    .andExpect(jsonPath("$.data[0].reportType").value("INTERVIEW"))
                    .andExpect(jsonPath("$.data[1].reportId").value("rpt-002"))
                    .andExpect(jsonPath("$.data[1].overallScore").value(90));

            verify(reportService).listReports(USER_ID);
        }

        @Test
        @DisplayName("无报告 → 返回空列表")
        void listReports_empty_shouldReturnEmptyList() throws Exception {
            when(reportService.listReports(USER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/offerpilot/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("服务层异常 → 500 内部错误")
        void listReports_serviceError_shouldReturn500() throws Exception {
            when(reportService.listReports(USER_ID))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/v1/offerpilot/reports"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("服务器内部错误"));
        }
    }

    // ==================== GET /api/v1/offerpilot/reports/{reportId} ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/reports/{reportId}")
    class GetReportTests {

        @Test
        @DisplayName("正常查询 → 200 + 单个报告")
        void getReport_shouldReturn200() throws Exception {
            AnalysisReport report = buildReport("rpt-001", 85);
            when(analysisService.getReportByReportId("rpt-001")).thenReturn(report);

            mockMvc.perform(get("/api/v1/offerpilot/reports/rpt-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.reportId").value("rpt-001"))
                    .andExpect(jsonPath("$.data.overallScore").value(85))
                    .andExpect(jsonPath("$.data.dimensionsJson").value("{\"technical\":80,\"communication\":75}"));

            verify(analysisService).getReportByReportId("rpt-001");
        }

        @Test
        @DisplayName("报告不存在 → 404")
        void getReport_notFound_shouldReturn404() throws Exception {
            when(analysisService.getReportByReportId("rpt-999"))
                    .thenThrow(new BusinessException(404, "报告不存在"));

            mockMvc.perform(get("/api/v1/offerpilot/reports/rpt-999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("报告不存在"));
        }

        @Test
        @DisplayName("不同 reportType → 返回正确类型")
        void getReport_differentType_shouldReflectType() throws Exception {
            AnalysisReport report = buildReport("rpt-003", 70);
            report.setReportType("RESUME");
            when(analysisService.getReportByReportId("rpt-003")).thenReturn(report);

            mockMvc.perform(get("/api/v1/offerpilot/reports/rpt-003"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reportType").value("RESUME"));
        }
    }

    // ==================== POST /api/v1/offerpilot/reports ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/reports")
    class GenerateReportTests {

        @Test
        @DisplayName("正常生成 → 200 + reportId")
        void generateReport_shouldReturn200() throws Exception {
            when(reportService.generateReport(USER_ID, "sess-001")).thenReturn("rpt-new-001");

            mockMvc.perform(post("/api/v1/offerpilot/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sessionId": "sess-001"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("rpt-new-001"));

            verify(reportService).generateReport(USER_ID, "sess-001");
        }

        @Test
        @DisplayName("无 sessionId → 正常生成（sessionId=null）")
        void generateReport_noSessionId_shouldSucceed() throws Exception {
            when(reportService.generateReport(USER_ID, null)).thenReturn("rpt-new-002");

            mockMvc.perform(post("/api/v1/offerpilot/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("rpt-new-002"));

            verify(reportService).generateReport(USER_ID, null);
        }

        @Test
        @DisplayName("生成失败 → 业务异常透传")
        void generateReport_serviceError_shouldPropagate() throws Exception {
            when(reportService.generateReport(USER_ID, "sess-fail"))
                    .thenThrow(new BusinessException(400, "会话不存在"));

            mockMvc.perform(post("/api/v1/offerpilot/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sessionId": "sess-fail"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("会话不存在"));
        }
    }
}
