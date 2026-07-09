/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.entity.AnalysisReport;
import com.tutorial.offerpilot.service.InterviewAnalysisService;
import com.tutorial.offerpilot.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/offerpilot/reports")
@RequiredArgsConstructor
public class ReportController {

    private final InterviewAnalysisService analysisService;
    private final ReportService reportService;

    @GetMapping
    public ApiResponse<List<AnalysisReport>> listReports(
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = currentUser.getUsername();
        log.info("List reports: userId={}", userId);
        return ApiResponse.success(reportService.listReports(userId));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<AnalysisReport> getReport(@PathVariable String reportId) {
        log.info("Get report: reportId={}", reportId);
        return ApiResponse.success(analysisService.getReportByReportId(reportId));
    }

    @PostMapping
    public ApiResponse<String> generateReport(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = currentUser.getUsername();
        String sessionId = body.get("sessionId");
        log.info("Generate report: userId={}, sessionId={}", userId, sessionId);
        String reportId = reportService.generateReport(userId, sessionId);
        return ApiResponse.success(reportId);
    }
}
