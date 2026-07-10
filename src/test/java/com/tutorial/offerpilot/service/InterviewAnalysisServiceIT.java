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
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

@DisplayName("InterviewAnalysisService 集成测试")
class InterviewAnalysisServiceIT extends AbstractServiceIT {

    @Autowired
    private InterviewAnalysisService analysisService;

    @Autowired
    private InterviewSessionRepository sessionRepo;

    @Autowired
    private InterviewQuestionRepository questionRepo;

    // ==================== saveAnalysis ====================

    @Nested
    @DisplayName("saveAnalysis")
    class SaveAnalysisTests {

        @Test
        @DisplayName("新问题 → 创建 InterviewQuestion + KnowledgeMastery")
        void saveAnalysis_newQuestion_shouldCreateQuestionAndMastery() {
            // Arrange: create session via JPA
            InterviewSession session = new InterviewSession();
            session.setSessionId("sess-save-001");
            session.setUserId("ia-test-user-1");
            session.setSessionType("TECHNICAL");
            session.setInterviewMode("text");
            session.setStatus("ACTIVE");
            session.setStartedAt(Instant.now());
            sessionRepo.saveAndFlush(session);

            // Act
            analysisService.saveAnalysis(
                    "sess-save-001", "请介绍一下Spring的依赖注入原理",
                    "DI是一种设计模式...", 85, 80, 90,
                    "回答逻辑清晰", "缺少具体示例");

            // Assert
            runVerify("interview-analysis/save-new");
        }

        @Test
        @DisplayName("已有问题 → 更新评分和反馈")
        void saveAnalysis_existingQuestion_shouldUpdateScores() {
            // Arrange: create session via JPA
            InterviewSession session = new InterviewSession();
            session.setSessionId("sess-save-002");
            session.setUserId("ia-test-user");
            session.setSessionType("TECHNICAL");
            session.setInterviewMode("text");
            session.setStatus("ACTIVE");
            session.setStartedAt(Instant.now());
            sessionRepo.saveAndFlush(session);

            // First save creates the question
            analysisService.saveAnalysis(
                    "sess-save-002", "谈谈你对微服务的理解",
                    "微服务是...", 70, 75, 65,
                    "基本概念正确", "缺乏实践经验");

            // Second save with same questionText should update
            analysisService.saveAnalysis(
                    "sess-save-002", "谈谈你对微服务的理解",
                    "微服务架构...", 90, 85, 88,
                    "深度理解", "none");

            runVerify("interview-analysis/save-update");
        }
    }

    // ==================== generateReport ====================

    @Nested
    @DisplayName("generateReport")
    class GenerateReportTests {

        @Test
        @DisplayName("有评分问题的Session → 生成分析报告")
        void generateReport_withScoredQuestions_shouldCreateReport() {
            // Arrange: create session + questions via JPA
            InterviewSession session = new InterviewSession();
            session.setSessionId("sess-gen-001");
            session.setUserId("ia-test-user");
            session.setSessionType("TECHNICAL");
            session.setInterviewMode("voice");
            session.setStatus("ACTIVE");
            session.setStartedAt(Instant.now());
            session.setQuestionCount(2);
            sessionRepo.saveAndFlush(session);

            InterviewQuestion q1 = new InterviewQuestion();
            q1.setSessionId("sess-gen-001");
            q1.setQuestionId("q-001");
            q1.setQuestionText("请介绍Java的内存模型");
            q1.setAnswerText("堆栈方法区...");
            q1.setTechScore(85);
            q1.setExprScore(80);
            q1.setCoverageScore(90);
            q1.setSortOrder(0);
            questionRepo.saveAndFlush(q1);

            InterviewQuestion q2 = new InterviewQuestion();
            q2.setSessionId("sess-gen-001");
            q2.setQuestionId("q-002");
            q2.setQuestionText("什么是GC，如何调优");
            q2.setAnswerText("GC是垃圾回收...");
            q2.setTechScore(70);
            q2.setExprScore(75);
            q2.setCoverageScore(65);
            q2.setSortOrder(1);
            questionRepo.saveAndFlush(q2);

            // Act
            AnalysisReport report = analysisService.generateReport("sess-gen-001");

            // Assert
            assertNotNull(report);
            assertNotNull(report.getReportId());
            assertTrue(report.getReportId().startsWith("rpt-"));
            assertEquals("INTERVIEW_ANALYSIS", report.getReportType());
            assertNotNull(report.getOverallScore());
            assertNotNull(report.getDimensionsJson());
            assertNotNull(report.getDetailsJson());
            runVerify("interview-analysis/generate");
        }

        @Test
        @DisplayName("Session不存在 → 抛出异常")
        void generateReport_nonexistentSession_shouldThrow() {
            assertThrows(BusinessException.class,
                    () -> analysisService.generateReport("sess-not-exist"));
        }

        @Test
        @DisplayName("Session无问题 → 抛出异常")
        void generateReport_sessionWithNoQuestions_shouldThrow() {
            // Arrange: create empty session via JPA
            InterviewSession session = new InterviewSession();
            session.setSessionId("sess-gen-empty");
            session.setUserId("ia-test-user");
            session.setSessionType("TECHNICAL");
            session.setInterviewMode("text");
            session.setStatus("ACTIVE");
            session.setStartedAt(Instant.now());
            sessionRepo.saveAndFlush(session);

            assertThrows(BusinessException.class,
                    () -> analysisService.generateReport("sess-gen-empty"));
        }
    }

    // ==================== getReportsByUserId ====================

    @Nested
    @DisplayName("getReportsByUserId")
    class GetReportsTests {

        @Test
        @DisplayName("有报告的用户 → 返回报告列表")
        void getReportsByUserId_withReports_shouldReturnList() {
            // Arrange: create session + questions, then generate report
            InterviewSession session = new InterviewSession();
            session.setSessionId("sess-gen-002");
            session.setUserId("ia-test-user");
            session.setSessionType("TECHNICAL");
            session.setInterviewMode("text");
            session.setStatus("ACTIVE");
            session.setStartedAt(Instant.now());
            session.setQuestionCount(1);
            sessionRepo.saveAndFlush(session);

            InterviewQuestion q = new InterviewQuestion();
            q.setSessionId("sess-gen-002");
            q.setQuestionText("测试问题");
            q.setAnswerText("测试答案");
            q.setTechScore(80);
            q.setExprScore(80);
            q.setCoverageScore(80);
            q.setSortOrder(0);
            questionRepo.saveAndFlush(q);

            // Act
            analysisService.generateReport("sess-gen-002");
            var reports = analysisService.getReportsByUserId("ia-test-user");

            // Assert
            assertFalse(reports.isEmpty());
            assertTrue(reports.stream().allMatch(r -> "ia-test-user".equals(r.getUserId())));
        }
    }
}

