/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.AnalysisReport;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.InterviewSession;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.repository.AnalysisReportRepository;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnalysisService {

    private final AnalysisReportRepository reportRepo;
    private final KnowledgeMasteryRepository masteryRepo;
    private final InterviewQuestionRepository questionRepo;
    private final InterviewSessionRepository sessionRepo;

    public List<AnalysisReport> getReportsByUserId(String userId) {
        return reportRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public AnalysisReport getReportByReportId(String reportId) {
        return reportRepo.findByReportId(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在: " + reportId));
    }

    /**
     * 保存面试回答分析结果到 InterviewQuestion 并更新 KnowledgeMastery。
     */
    @Transactional
    public void saveAnalysis(String sessionId, String questionText, String answerText,
                             Integer techScore, Integer exprScore, Integer coverageScore,
                             String highlights, String weaknesses) {
        List<InterviewQuestion> questions = questionRepo.findBySessionIdOrderBySortOrder(sessionId);
        InterviewQuestion question = questions.stream()
                .filter(q -> questionText.equals(q.getQuestionText()))
                .findFirst()
                .orElseGet(() -> {
                    InterviewQuestion q = new InterviewQuestion();
                    q.setSessionId(sessionId);
                    q.setQuestionText(questionText);
                    q.setSortOrder(questions.size());
                    return q;
                });

        question.setAnswerText(answerText);
        question.setTechScore(techScore);
        question.setExprScore(exprScore);
        question.setCoverageScore(coverageScore);
        question.setHighlights(highlights);
        question.setWeaknesses(weaknesses);
        questionRepo.save(question);

        String knowledgePoint = extractKnowledgePoint(questionText);
        updateMastery(question.getSessionId(), knowledgePoint, techScore);

        log.info("Analysis saved: sessionId={}, question={}, techScore={}, exprScore={}, coverageScore={}",
                sessionId, questionText, techScore, exprScore, coverageScore);
    }

    /**
     * 从面试 Session 聚合生成分析报告。
     */
    @Transactional
    public AnalysisReport generateReport(String sessionId) {
        InterviewSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<InterviewQuestion> questions = questionRepo.findBySessionIdOrderBySortOrder(sessionId);
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("No questions found for session: " + sessionId);
        }

        double avgScore = questions.stream()
                .filter(q -> q.getTechScore() != null && q.getExprScore() != null && q.getCoverageScore() != null)
                .mapToDouble(q -> (q.getTechScore() + q.getExprScore() + q.getCoverageScore()) / 3.0)
                .average()
                .orElse(0);

        int overallScore = (int) Math.round(avgScore);

        Map<String, Object> dimensions = buildDimensions(questions);
        List<Map<String, Object>> details = buildDetails(questions);
        List<String> improvements = buildImprovements(questions, overallScore);

        AnalysisReport report = new AnalysisReport();
        report.setReportId("rpt-" + UUID.randomUUID().toString().substring(0, 8));
        report.setUserId(session.getUserId());
        report.setSessionId(sessionId);
        report.setReportType("INTERVIEW_ANALYSIS");
        report.setOverallScore(overallScore);
        report.setDimensionsJson(JsonUtil.toJson(dimensions));
        report.setDetailsJson(JsonUtil.toJson(details));
        report.setImprovementsJson(JsonUtil.toJson(improvements));

        reportRepo.save(report);
        session.setOverallScore(overallScore);
        sessionRepo.save(session);

        log.info("Report generated: reportId={}, sessionId={}, overallScore={}, questions={}",
                report.getReportId(), sessionId, overallScore, questions.size());
        return report;
    }

    private String extractKnowledgePoint(String questionText) {
        if (questionText == null || questionText.length() < 3) {
            return "通用面试";
        }
        return questionText.substring(0, Math.min(questionText.length(), 20));
    }

    private void updateMastery(String sessionId, String knowledgePoint, Integer score) {
        InterviewSession session = sessionRepo.findBySessionId(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        KnowledgeMastery mastery = masteryRepo
                .findByUserIdAndKnowledgePoint(session.getUserId(), knowledgePoint)
                .orElseGet(() -> {
                    KnowledgeMastery m = new KnowledgeMastery();
                    m.setUserId(session.getUserId());
                    m.setKnowledgePoint(knowledgePoint);
                    m.setAssessCount(0);
                    return m;
                });

        mastery.setPreviousScore(mastery.getScore());
        mastery.setScore(score);
        mastery.setAssessCount(mastery.getAssessCount() + 1);
        mastery.setLastAssessed(Instant.now());
        masteryRepo.save(mastery);
    }

    private Map<String, Object> buildDimensions(List<InterviewQuestion> questions) {
        Map<String, Object> dims = new LinkedHashMap<>();
        double avgTech = averageScore(questions, InterviewQuestion::getTechScore);
        double avgExpr = averageScore(questions, InterviewQuestion::getExprScore);
        double avgCoverage = averageScore(questions, InterviewQuestion::getCoverageScore);
        dims.put("techScore", Math.round(avgTech));
        dims.put("exprScore", Math.round(avgExpr));
        dims.put("coverageScore", Math.round(avgCoverage));
        return dims;
    }

    private List<Map<String, Object>> buildDetails(List<InterviewQuestion> questions) {
        return questions.stream()
                .filter(q -> q.getTechScore() != null)
                .map(q -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("question", q.getQuestionText());
                    detail.put("techScore", q.getTechScore());
                    detail.put("exprScore", q.getExprScore());
                    detail.put("coverageScore", q.getCoverageScore());
                    detail.put("avg", (q.getTechScore() + q.getExprScore() + q.getCoverageScore()) / 3);
                    return detail;
                })
                .toList();
    }

    private List<String> buildImprovements(List<InterviewQuestion> questions, int overallScore) {
        List<String> improvements = new java.util.ArrayList<>();
        if (overallScore < 60) {
            improvements.add("建议加强基础知识学习，重点提升技术回答的准确性");
        }
        if (overallScore < 80) {
            improvements.add("建议多进行模拟面试练习，提升表达流畅度");
        }
        long weakCoverage = questions.stream()
                .filter(q -> q.getCoverageScore() != null && q.getCoverageScore() < 60)
                .count();
        if (weakCoverage > questions.size() / 2) {
            improvements.add("回答覆盖面不足，建议补充更多维度的内容");
        }
        return improvements;
    }

    private double averageScore(List<InterviewQuestion> questions,
                                java.util.function.Function<InterviewQuestion, Integer> extractor) {
        return questions.stream()
                .map(extractor)
                .filter(s -> s != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }
}
