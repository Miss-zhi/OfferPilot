/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.AnalysisReport;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.InterviewSession;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewAnalysisService 单元测试")
class InterviewAnalysisServiceTest {

    @Mock private AnalysisReportRepository reportRepo;
    @Mock private KnowledgeMasteryRepository masteryRepo;
    @Mock private InterviewQuestionRepository questionRepo;
    @Mock private InterviewSessionRepository sessionRepo;

    @InjectMocks
    private InterviewAnalysisService analysisService;

    private static final String USER_ID = "u-001";
    private static final String SESSION_ID = "s-001";

    // ==================== getReportsByUserId ====================

    @Nested
    @DisplayName("getReportsByUserId")
    class GetReportsTests {

        @Test
        @DisplayName("有报告 → 返回列表")
        void getReportsByUserId_shouldReturnList() {
            AnalysisReport r = new AnalysisReport();
            r.setReportId("rpt-001");
            when(reportRepo.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(r));

            List<AnalysisReport> result = analysisService.getReportsByUserId(USER_ID);

            assertEquals(1, result.size());
            assertEquals("rpt-001", result.get(0).getReportId());
        }

        @Test
        @DisplayName("无报告 → 返回空列表")
        void getReportsByUserId_empty_shouldReturnEmpty() {
            when(reportRepo.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Collections.emptyList());
            assertTrue(analysisService.getReportsByUserId(USER_ID).isEmpty());
        }
    }

    // ==================== getReportByReportId ====================

    @Nested
    @DisplayName("getReportByReportId")
    class GetReportByIdTests {

        @Test
        @DisplayName("存在 → 返回报告")
        void getReportByReportId_found_shouldReturn() {
            AnalysisReport r = new AnalysisReport();
            r.setReportId("rpt-001");
            when(reportRepo.findByReportId("rpt-001")).thenReturn(Optional.of(r));

            AnalysisReport result = analysisService.getReportByReportId("rpt-001");
            assertEquals("rpt-001", result.getReportId());
        }

        @Test
        @DisplayName("不存在 → 抛异常")
        void getReportByReportId_notFound_shouldThrow() {
            when(reportRepo.findByReportId("rpt-xxx")).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class,
                    () -> analysisService.getReportByReportId("rpt-xxx"));
        }
    }

    // ==================== saveAnalysis ====================

    @Nested
    @DisplayName("saveAnalysis")
    class SaveAnalysisTests {

        @Test
        @DisplayName("已有 question → 更新字段")
        void saveAnalysis_existingQuestion_shouldUpdate() {
            InterviewQuestion existing = new InterviewQuestion();
            existing.setSessionId(SESSION_ID);
            existing.setQuestionText("Java 多态");
            existing.setSortOrder(0);
            when(questionRepo.findBySessionIdOrderBySortOrder(SESSION_ID)).thenReturn(List.of(existing));
            when(sessionRepo.findBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(mockSession()));

            analysisService.saveAnalysis(SESSION_ID, "Java 多态", "多态是...", 80, 75, 70, "亮点", "弱点");

            verify(questionRepo).save(existing);
            assertEquals("多态是...", existing.getAnswerText());
            assertEquals(80, existing.getTechScore());
            assertEquals(75, existing.getExprScore());
            assertEquals(70, existing.getCoverageScore());
        }

        @Test
        @DisplayName("新 question → 创建新记录")
        void saveAnalysis_newQuestion_shouldCreate() {
            when(questionRepo.findBySessionIdOrderBySortOrder(SESSION_ID)).thenReturn(Collections.emptyList());
            when(sessionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.of(mockSession()));

            analysisService.saveAnalysis(SESSION_ID, "新题目", "答案", 90, 85, 80, null, null);

            ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
            verify(questionRepo).save(captor.capture());
            InterviewQuestion saved = captor.getValue();
            assertEquals("新题目", saved.getQuestionText());
            assertEquals(SESSION_ID, saved.getSessionId());
        }
    }

    // ==================== generateReport ====================

    @Nested
    @DisplayName("generateReport")
    class GenerateReportTests {

        @Test
        @DisplayName("正常生成 → 返回报告，更新 Session")
        void generateReport_shouldReturnReport() {
            InterviewSession session = mockSession();
            when(sessionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));

            InterviewQuestion q = new InterviewQuestion();
            q.setTechScore(80);
            q.setExprScore(75);
            q.setCoverageScore(70);
            q.setQuestionText("Q1");
            when(questionRepo.findBySessionIdOrderBySortOrder(SESSION_ID)).thenReturn(List.of(q));

            AnalysisReport result = analysisService.generateReport(SESSION_ID);

            assertNotNull(result);
            assertTrue(result.getReportId().startsWith("rpt-"));
            assertEquals(USER_ID, result.getUserId());
            assertEquals(SESSION_ID, result.getSessionId());

            // score = (80+75+70)/3 ≈ 75
            assertNotNull(result.getOverallScore());

            verify(reportRepo).save(any());
            verify(sessionRepo).save(session);
        }

        @Test
        @DisplayName("无 questions → 抛异常")
        void generateReport_noQuestions_shouldThrow() {
            when(sessionRepo.findBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(mockSession()));
            when(questionRepo.findBySessionIdOrderBySortOrder(SESSION_ID))
                    .thenReturn(Collections.emptyList());

            assertThrows(IllegalArgumentException.class,
                    () -> analysisService.generateReport(SESSION_ID));
        }

        @Test
        @DisplayName("Session 不存在 → 抛异常")
        void generateReport_sessionNotFound_shouldThrow() {
            when(sessionRepo.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> analysisService.generateReport(SESSION_ID));
        }
    }

    private InterviewSession mockSession() {
        InterviewSession s = new InterviewSession();
        s.setSessionId(SESSION_ID);
        s.setUserId(USER_ID);
        s.setSessionType("MOCK");
        return s;
    }
}
