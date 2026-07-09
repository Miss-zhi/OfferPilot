/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.NextQuestionResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.InterviewSession;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MockInterviewTool 单元测试")
class MockInterviewToolTest {

    @Mock
    private InterviewQuestionRepository questionRepo;

    @Mock
    private InterviewSessionRepository sessionRepo;

    @InjectMocks
    private MockInterviewTool tool;

    // ==================== generateNextQuestion - 分类识别 ====================

    @Nested
    @DisplayName("generateNextQuestion - 分类识别")
    class CategoryDetectionTests {

        @Test
        @DisplayName("null context → 通用分类 + easy 难度")
        void nullContext_shouldReturnDefault() {
            NextQuestionResult result = tool.generateNextQuestion(null);

            assertNotNull(result);
            assertEquals("通用", result.getCategory());
            assertEquals("medium", result.getDifficulty());
            assertNotNull(result.getQuestion());
            assertTrue(result.getReason().contains("第1题"));
        }

        @Test
        @DisplayName("context 含 'Java' → Java基础")
        void contextWithJava_shouldReturnJavaCategory() {
            String ctx = "session=test123, 考察Java八股文";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertEquals("Java基础", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '并发' → 并发编程")
        void contextWithConcurrency_shouldReturnConcurrencyCategory() {
            NextQuestionResult result = tool.generateNextQuestion("session=s1, 并发编程 线程池");

            assertEquals("并发编程", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '数据库' → 数据库")
        void contextWithDatabase_shouldReturnDatabaseCategory() {
            NextQuestionResult result = tool.generateNextQuestion("考察 MySQL 数据库知识");

            assertEquals("数据库", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '设计' → 系统设计")
        void contextWithDesign_shouldReturnSystemDesignCategory() {
            NextQuestionResult result = tool.generateNextQuestion("系统设计 微服务架构");

            assertEquals("系统设计", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '锁' → 并发编程")
        void contextWithLock_shouldReturnConcurrencyCategory() {
            NextQuestionResult result = tool.generateNextQuestion("讨论 锁 和 synchronized");

            assertEquals("并发编程", result.getCategory());
        }

        @Test
        @DisplayName("context 含 'sql' → 数据库")
        void contextWithSql_shouldReturnDatabaseCategory() {
            NextQuestionResult result = tool.generateNextQuestion("sql优化 慢查询");

            assertEquals("数据库", result.getCategory());
        }
    }

    // ==================== generateNextQuestion - 难度分级 ====================

    @Nested
    @DisplayName("generateNextQuestion - 难度分级")
    class DifficultyTests {

        @Test
        @DisplayName("context 含 '高级' → hard")
        void contextWithAdvanced_shouldReturnHard() {
            NextQuestionResult result = tool.generateNextQuestion("Java 高级 深入理解");

            assertEquals("hard", result.getDifficulty());
        }

        @Test
        @DisplayName("context 含 '初级' → easy")
        void contextWithBeginner_shouldReturnEasy() {
            NextQuestionResult result = tool.generateNextQuestion("Java 初级 入门");

            assertEquals("easy", result.getDifficulty());
        }

        @Test
        @DisplayName("超过5个Q → hard（渐进式难度提升）")
        void moreThan5Questions_shouldReturnHard() {
            String ctx = "Q1 xxx Q2 xxx Q3 xxx Q4 xxx Q5 xxx Q6 xxx Java";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertEquals("hard", result.getDifficulty());
        }

        @Test
        @DisplayName("3个Q → medium")
        void threeQuestions_shouldReturnMedium() {
            String ctx = "Q1 xxx Q2 xxx Q3 xxx Java";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertEquals("medium", result.getDifficulty());
        }

        @Test
        @DisplayName("1个Q → easy")
        void oneQuestion_shouldReturnEasy() {
            String ctx = "Q1 Java基础";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertEquals("easy", result.getDifficulty());
        }
    }

    // ==================== generateNextQuestion - SESSION_ID 解析 ====================

    @Nested
    @DisplayName("generateNextQuestion - SessionId 解析")
    class SessionIdTests {

        @Test
        @DisplayName("session=xxx → 解析出 sessionId")
        void sessionEquals_shouldParse() {
            NextQuestionResult result = tool.generateNextQuestion("session=abc-123 Java");

            assertNotNull(result);
            // called by determineDifficultyFromScores + persistQuestion
            verify(questionRepo, atLeastOnce()).findBySessionIdOrderBySortOrder("abc-123");
        }

        @Test
        @DisplayName("session:xxx → 解析出 sessionId")
        void sessionColon_shouldParse() {
            NextQuestionResult result = tool.generateNextQuestion("session:xyz Java");

            verify(questionRepo, atLeastOnce()).findBySessionIdOrderBySortOrder("xyz");
        }

        @Test
        @DisplayName("无 session 模式 → 不查询 DB")
        void noSession_shouldNotQueryDb() {
            NextQuestionResult result = tool.generateNextQuestion("Java基础面试");

            verify(questionRepo, never()).findBySessionIdOrderBySortOrder(anyString());
            verify(sessionRepo, never()).findBySessionId(anyString());
        }
    }

    // ==================== generateNextQuestion - 题目选取 ====================

    @Nested
    @DisplayName("generateNextQuestion - 题目选取")
    class QuestionSelectionTests {

        @Test
        @DisplayName("首个问题 → 题库第一题")
        void firstQuestion_shouldReturnFirstFromBank() {
            NextQuestionResult result = tool.generateNextQuestion("Java基础");

            assertEquals("请解释Java中HashMap的实现原理", result.getQuestion());
        }

        @Test
        @DisplayName("第2个问题 → 题库第2题")
        void secondQuestion_shouldReturnSecondFromBank() {
            String ctx = "Q1 xxx Java基础";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertEquals("谈谈Java内存模型（JMM）的理解", result.getQuestion());
        }

        @Test
        @DisplayName("超出题库数量 → 循环回到第1题")
        void exceedBankSize_shouldWrapAround() {
            // Java基础有4题，第5题应回到第1题
            String ctx = "Q1 Q2 Q3 Q4 Java基础";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertEquals("请解释Java中HashMap的实现原理", result.getQuestion());
        }
    }

    // ==================== generateNextQuestion - 持久化 ====================

    @Nested
    @DisplayName("generateNextQuestion - 持久化")
    class PersistenceTests {

        @Test
        @DisplayName("有 sessionId → 保存 Question 并更新 Session")
        void withSessionId_shouldPersist() {
            when(questionRepo.findBySessionIdOrderBySortOrder("s1")).thenReturn(List.of());
            InterviewSession session = new InterviewSession();
            session.setSessionId("s1");
            session.setQuestionCount(3);
            when(sessionRepo.findBySessionId("s1")).thenReturn(Optional.of(session));

            tool.generateNextQuestion("session=s1 Java");

            // 验证保存了 InterviewQuestion
            ArgumentCaptor<InterviewQuestion> qCaptor = ArgumentCaptor.forClass(InterviewQuestion.class);
            verify(questionRepo).save(qCaptor.capture());
            assertEquals("s1", qCaptor.getValue().getSessionId());
            assertEquals(0, qCaptor.getValue().getSortOrder());

            // 验证更新了 session questionCount
            verify(sessionRepo).save(session);
            assertEquals(4, session.getQuestionCount());
        }

        @Test
        @DisplayName("无 sessionId → 不持久化")
        void withoutSessionId_shouldNotPersist() {
            tool.generateNextQuestion("Java");

            verify(questionRepo, never()).save(any());
            verify(sessionRepo, never()).findBySessionId(anyString());
        }
    }

    // ==================== generateNextQuestion - Reason 构建 ====================

    @Nested
    @DisplayName("generateNextQuestion - Reason 构建")
    class ReasonTests {

        @Test
        @DisplayName("reason 包含题号、分类、难度")
        void reason_shouldContainMetadata() {
            NextQuestionResult result = tool.generateNextQuestion("Java基础");

            assertTrue(result.getReason().contains("第1题"));
            assertTrue(result.getReason().contains("Java基础"));
            assertTrue(result.getReason().contains("easy"));
        }
    }
}
