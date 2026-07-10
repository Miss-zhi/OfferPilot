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
        @DisplayName("null context → 自我介绍 + easy 难度")
        void nullContext_shouldReturnDefault() {
            NextQuestionResult result = tool.generateNextQuestion(null);

            assertNotNull(result);
            assertEquals("自我介绍", result.getCategory());
            assertEquals("easy", result.getDifficulty());
            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("第1题"));
        }

        @Test
        @DisplayName("context 含 '技术' → 专业技能")
        void contextWithTech_shouldReturnProfessionalSkill() {
            String ctx = "session=test123, 考察技术能力 微服务架构";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertEquals("专业技能", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '项目' → 项目经验")
        void contextWithProject_shouldReturnProjectExperience() {
            NextQuestionResult result = tool.generateNextQuestion("session=s1, 讨论项目经验 案例");

            assertEquals("项目经验", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '情景' → 情景分析")
        void contextWithScenario_shouldReturnScenarioAnalysis() {
            NextQuestionResult result = tool.generateNextQuestion("情景分析 假设场景");

            assertEquals("情景分析", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '行为' → 行为面试")
        void contextWithBehavior_shouldReturnBehavioral() {
            NextQuestionResult result = tool.generateNextQuestion("行为面试 压力测试 冲突处理");

            assertEquals("行为面试", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '规划' → 职业规划")
        void contextWithCareer_shouldReturnCareerPlanning() {
            NextQuestionResult result = tool.generateNextQuestion("讨论 规划 和 职业目标");

            assertEquals("职业规划", result.getCategory());
        }

        @Test
        @DisplayName("context 含 '介绍自己' → 自我介绍")
        void contextWithSelfIntro_shouldReturnSelfIntroduction() {
            NextQuestionResult result = tool.generateNextQuestion("介绍自己 开场白");

            assertEquals("自我介绍", result.getCategory());
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
    @DisplayName("generateNextQuestion - 出题指导")
    class QuestionSelectionTests {

        @Test
        @DisplayName("首个问题 → 返回出题指导（含题号+阶段）")
        void firstQuestion_shouldReturnGuidance() {
            NextQuestionResult result = tool.generateNextQuestion("技术面试");

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("第1题"));
            assertTrue(result.getGuidance().contains("专业技能"));
        }

        @Test
        @DisplayName("第2个问题 → 返回出题指导（含题号+阶段）")
        void secondQuestion_shouldReturnGuidance() {
            String ctx = "Q1 xxx 技术面试";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("第2题"));
            assertTrue(result.getGuidance().contains("专业技能"));
        }

        @Test
        @DisplayName("第5个问题 → 返回出题指导（含题号+阶段）")
        void exceedBankSize_shouldReturnGuidance() {
            String ctx = "Q1 Q2 Q3 Q4 技术面试";

            NextQuestionResult result = tool.generateNextQuestion(ctx);

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("第5题"));
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

    // ==================== generateNextQuestion - Guidance 构建 ====================

    @Nested
    @DisplayName("generateNextQuestion - Guidance 构建")
    class GuidanceTests {

        @Test
        @DisplayName("guidance 包含题号、阶段分类")
        void guidance_shouldContainMetadata() {
            NextQuestionResult result = tool.generateNextQuestion("技术面试");

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("第1题"));
            assertTrue(result.getGuidance().contains("专业技能"));
        }
    }
}
