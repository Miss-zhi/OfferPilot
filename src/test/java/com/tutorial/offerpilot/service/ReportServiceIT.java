/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.entity.AnalysisReport;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.InterviewSession;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.repository.AnalysisReportRepository;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

@DisplayName("ReportService 集成测试")
class ReportServiceIT extends AbstractServiceIT {

    @Autowired
    private ReportService reportService;

    @Autowired
    private InterviewSessionRepository sessionRepo;

    @Autowired
    private InterviewQuestionRepository questionRepo;

    @Autowired
    private AnalysisReportRepository reportRepo;

    // ==================== generateReport ====================

    @Nested
    @DisplayName("generateReport")
    class GenerateReportTests {

        @Test
        @DisplayName("有效Session → 生成报告并返回reportId")
        void generateReport_validSession_shouldReturnReportId() {
            // Arrange: session + questions via JPA
            InterviewSession session = new InterviewSession();
            session.setSessionId("sess-rpt-001");
            session.setUserId("rpt-test-user");
            session.setSessionType("TECHNICAL");
            session.setInterviewMode("text");
            session.setStatus("ACTIVE");
            session.setStartedAt(Instant.now());
            sessionRepo.saveAndFlush(session);

            InterviewQuestion q1 = new InterviewQuestion();
            q1.setSessionId("sess-rpt-001");
            q1.setQuestionText("请介绍Redis的数据结构");
            q1.setAnswerText("String、Hash、List...");
            q1.setTechScore(90);
            q1.setExprScore(85);
            q1.setCoverageScore(80);
            q1.setSortOrder(0);
            questionRepo.saveAndFlush(q1);

            // Act
            String reportId = reportService.generateReport("rpt-test-user", "sess-rpt-001");

            // Assert
            assertNotNull(reportId);
            assertTrue(reportId.startsWith("rpt-"));
        }
    }

    // ==================== listReports ====================

    @Nested
    @DisplayName("listReports")
    class ListReportsTests {

        @Test
        @DisplayName("有报告的用户 → 返回报告列表")
        void listReports_withReports_shouldReturnList() {
            // Arrange: pre-insert reports via JPA
            AnalysisReport r1 = createReport("rpt-get-001", "rpt-list-user", "INTERVIEW_ANALYSIS", 85);
            AnalysisReport r2 = createReport("rpt-get-002", "rpt-list-user", "RESUME_ANALYSIS", 70);

            // Act
            List<AnalysisReport> reports = reportService.listReports("rpt-list-user");

            // Assert
            assertFalse(reports.isEmpty());
            assertEquals(2, reports.size());
            assertTrue(reports.stream().allMatch(r -> "rpt-list-user".equals(r.getUserId())));
        }
    }

    // ==================== getReport ====================

    @Nested
    @DisplayName("getReport")
    class GetReportTests {

        @Test
        @DisplayName("存在的报告 → 返回报告详情")
        void getReport_existing_shouldReturnDetail() {
            createReport("rpt-get-001", "rpt-list-user", "INTERVIEW_ANALYSIS", 85);

            AnalysisReport report = reportService.getReport("rpt-get-001");

            assertNotNull(report);
            assertEquals("rpt-get-001", report.getReportId());
            assertEquals("rpt-list-user", report.getUserId());
            assertEquals("INTERVIEW_ANALYSIS", report.getReportType());
        }

        @Test
        @DisplayName("不存在的报告 → 抛出异常")
        void getReport_nonExisting_shouldThrow() {
            assertThrows(BusinessException.class,
                    () -> reportService.getReport("rpt-not-exist"));
        }
    }

    // ────────────────────────── helper ──────────────────────────

    private AnalysisReport createReport(String reportId, String userId, String type, int score) {
        AnalysisReport r = new AnalysisReport();
        r.setReportId(reportId);
        r.setUserId(userId);
        r.setSessionId("sess-" + reportId);
        r.setReportType(type);
        r.setOverallScore(score);
        r.setCreateBy("test");
        return reportRepo.saveAndFlush(r);
    }
}
