/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorial.offerpilot.entity.AnalysisReport;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.entity.StudyPlan;
import com.tutorial.offerpilot.repository.AnalysisReportRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.repository.StudyPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 每周学习报告定时任务。
 * 每周日 20:00 自动汇总本周学习数据，生成周报并持久化到 op_analysis_report。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final StudyPlanRepository planRepo;
    private final KnowledgeMasteryRepository masteryRepo;
    private final InterviewSessionRepository sessionRepo;
    private final AnalysisReportRepository reportRepo;
    private final ObjectMapper objectMapper;

    /**
     * 每周日 20:00 生成所有活跃用户的学习周报。
     */
    @Scheduled(cron = "0 0 20 ? * SUN")
    public void generateWeeklyReport() {
        log.info("Weekly report generation started");

        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEnd = LocalDate.now().with(DayOfWeek.SUNDAY);
        Instant weekStartInstant = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant weekEndInstant = weekEnd.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

        List<StudyPlan> activePlans = planRepo.findByStatus("ACTIVE");
        Set<String> userIds = new HashSet<>();
        for (StudyPlan plan : activePlans) {
            if (plan.getUserId() != null) {
                userIds.add(plan.getUserId());
            }
        }

        int reportCount = 0;
        for (String userId : userIds) {
            try {
                generateUserReport(userId, weekStart, weekEnd, weekStartInstant, weekEndInstant);
                reportCount++;
            } catch (Exception e) {
                log.error("Failed to generate weekly report for userId={}: {}", userId, e.getMessage());
            }
        }

        log.info("Weekly report generation completed: {} reports generated for {} users",
                reportCount, userIds.size());
    }

    private void generateUserReport(String userId, LocalDate weekStart, LocalDate weekEnd,
                                     Instant weekStartInstant, Instant weekEndInstant) {
        // 本周面试次数
        long weekInterviews = sessionRepo.countByUserIdAndStartedAtBetween(
                userId, weekStartInstant, weekEndInstant);

        // 本周完成学习任务数
        List<StudyPlan> plans = planRepo.findByUserIdAndStatus(userId, "ACTIVE");
        int weekCompleted = plans.stream()
                .mapToInt(p -> p.getCompletedCount() != null ? p.getCompletedCount() : 0)
                .sum();
        int weekTotal = plans.stream()
                .mapToInt(p -> p.getTotalCount() != null ? p.getTotalCount() : 0)
                .sum();

        // 本周掌握度变化
        List<KnowledgeMastery> masteries = masteryRepo.findByUserId(userId);
        int improved = 0;
        int declined = 0;
        List<Map<String, Object>> masteryDetails = new ArrayList<>();
        for (KnowledgeMastery m : masteries) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("knowledgePoint", m.getKnowledgePoint());
            detail.put("score", m.getScore());
            if (m.getPreviousScore() != null && m.getScore() != null) {
                detail.put("previousScore", m.getPreviousScore());
                if (m.getScore() > m.getPreviousScore()) {
                    detail.put("trend", "up");
                    improved++;
                } else if (m.getScore() < m.getPreviousScore()) {
                    detail.put("trend", "down");
                    declined++;
                } else {
                    detail.put("trend", "stable");
                }
            }
            masteryDetails.add(detail);
        }

        log.info("Weekly report data: userId={}, interviews={}, completed={}/{}, improved={}, declined={}",
                userId, weekInterviews, weekCompleted, weekTotal, improved, declined);

        // 生成报告并持久化
        try {
            AnalysisReport report = new AnalysisReport();
            report.setReportId("WR-" + userId + "-" + weekStart);
            report.setUserId(userId);
            report.setReportType("WEEKLY_REPORT");

            Map<String, Object> dimensions = new LinkedHashMap<>();
            dimensions.put("weekStart", weekStart.toString());
            dimensions.put("weekEnd", weekEnd.toString());
            dimensions.put("interviewCount", weekInterviews);
            dimensions.put("completedTasks", weekCompleted);
            dimensions.put("totalTasks", weekTotal);
            dimensions.put("improvedCount", improved);
            dimensions.put("declinedCount", declined);
            report.setDimensionsJson(objectMapper.writeValueAsString(dimensions));

            report.setDetailsJson(objectMapper.writeValueAsString(masteryDetails));
            reportRepo.save(report);

            log.info("Weekly report saved: reportId={}", report.getReportId());
        } catch (Exception e) {
            log.error("Failed to persist weekly report for userId={}: {}", userId, e.getMessage());
        }
    }
}
