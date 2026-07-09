/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.AnalysisReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService 单元测试")
class ReportServiceTest {

    @Mock
    private InterviewAnalysisService analysisService;

    @InjectMocks
    private ReportService reportService;

    private static final String USER_ID = "u-001";
    private static final String SESSION_ID = "s-001";
    private static final String REPORT_ID = "rpt-001";

    @Nested
    @DisplayName("generateReport")
    class GenerateReportTests {

        @Test
        @DisplayName("委托 InterviewAnalysisService 生成并返回 reportId")
        void generateReport_shouldDelegateAndReturnReportId() {
            AnalysisReport report = new AnalysisReport();
            report.setReportId(REPORT_ID);
            when(analysisService.generateReport(SESSION_ID)).thenReturn(report);

            String result = reportService.generateReport(USER_ID, SESSION_ID);

            assertEquals(REPORT_ID, result);
            verify(analysisService).generateReport(SESSION_ID);
        }
    }

    @Nested
    @DisplayName("listReports")
    class ListReportsTests {

        @Test
        @DisplayName("委托 InterviewAnalysisService 查询")
        void listReports_shouldDelegate() {
            AnalysisReport r = new AnalysisReport();
            r.setReportId(REPORT_ID);
            when(analysisService.getReportsByUserId(USER_ID)).thenReturn(List.of(r));

            List<AnalysisReport> result = reportService.listReports(USER_ID);

            assertEquals(1, result.size());
            assertEquals(REPORT_ID, result.get(0).getReportId());
            verify(analysisService).getReportsByUserId(USER_ID);
        }

        @Test
        @DisplayName("无报告 → 空列表")
        void listReports_empty_shouldReturnEmpty() {
            when(analysisService.getReportsByUserId(USER_ID)).thenReturn(Collections.emptyList());
            assertTrue(reportService.listReports(USER_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("getReport")
    class GetReportTests {

        @Test
        @DisplayName("委托 InterviewAnalysisService 查询详情")
        void getReport_shouldDelegate() {
            AnalysisReport r = new AnalysisReport();
            r.setReportId(REPORT_ID);
            when(analysisService.getReportByReportId(REPORT_ID)).thenReturn(r);

            AnalysisReport result = reportService.getReport(REPORT_ID);

            assertEquals(REPORT_ID, result.getReportId());
            verify(analysisService).getReportByReportId(REPORT_ID);
        }
    }
}
