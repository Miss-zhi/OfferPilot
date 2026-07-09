/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.AnalysisReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final InterviewAnalysisService analysisService;

    /**
     * 生成面试分析报告，持久化后返回 reportId。
     */
    public String generateReport(String userId, String sessionId) {
        log.info("Generating report: userId={}, sessionId={}", userId, sessionId);
        return analysisService.generateReport(sessionId).getReportId();
    }

    /**
     * 查询用户所有报告列表。
     */
    public List<AnalysisReport> listReports(String userId) {
        return analysisService.getReportsByUserId(userId);
    }

    /**
     * 查询单个报告详情。
     */
    public AnalysisReport getReport(String reportId) {
        return analysisService.getReportByReportId(reportId);
    }
}
