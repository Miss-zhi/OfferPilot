/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.NextQuestionResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.InterviewSession;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import com.tutorial.offerpilot.service.InterviewModeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

    @Mock private InterviewQuestionRepository questionRepo;
    @Mock private InterviewSessionRepository sessionRepo;
    @Spy private InterviewModeService modeService = new InterviewModeService();
    @InjectMocks private MockInterviewTool tool;

    @Nested @DisplayName("分类识别")
    class CategoryTests {
        @Test void nullContext() {
            NextQuestionResult r = tool.generateNextQuestion(null, null, null);
            assertEquals("自我介绍", r.getCategory());
            assertEquals("easy", r.getDifficulty());
        }
        @Test void tech() { assertEquals("专业技能", tool.generateNextQuestion("session=test, 技术", null, null).getCategory()); }
        @Test void project() { assertEquals("项目经验", tool.generateNextQuestion("session=s1, 项目经验", null, null).getCategory()); }
        @Test void scenario() { assertEquals("情景分析", tool.generateNextQuestion("情景分析 假设", null, null).getCategory()); }
        @Test void behavior() { assertEquals("行为面试", tool.generateNextQuestion("行为面试 冲突", null, null).getCategory()); }
        @Test void career() { assertEquals("职业规划", tool.generateNextQuestion("规划 职业目标", null, null).getCategory()); }
        @Test void selfIntro() { assertEquals("自我介绍", tool.generateNextQuestion("介绍自己", null, null).getCategory()); }
    }

    @Nested @DisplayName("难度分级")
    class DifficultyTests {
        @Test void hard() { assertEquals("hard", tool.generateNextQuestion("Java 高级", null, null).getDifficulty()); }
        @Test void easy() { assertEquals("easy", tool.generateNextQuestion("Java 初级", null, null).getDifficulty()); }
        @Test void q6hard() { assertEquals("hard", tool.generateNextQuestion("Q1 Q2 Q3 Q4 Q5 Q6 Java", null, null).getDifficulty()); }
        @Test void q3medium() { assertEquals("medium", tool.generateNextQuestion("Q1 Q2 Q3 Java", null, null).getDifficulty()); }
        @Test void q1easy() { assertEquals("easy", tool.generateNextQuestion("Q1 Java", null, null).getDifficulty()); }
    }

    @Nested @DisplayName("SessionId")
    class SessionIdTests {
        @Test void equals() { tool.generateNextQuestion("session=abc-123 Java", null, null); verify(questionRepo, atLeastOnce()).findBySessionIdOrderBySortOrder("abc-123"); }
        @Test void colon() { tool.generateNextQuestion("session:xyz Java", null, null); verify(questionRepo, atLeastOnce()).findBySessionIdOrderBySortOrder("xyz"); }
        @Test void none() { tool.generateNextQuestion("Java基础", null, null); verify(questionRepo, never()).findBySessionIdOrderBySortOrder(anyString()); }
    }

    @Nested @DisplayName("出题指导")
    class GuidanceTests {
        @Test void firstQ() { NextQuestionResult r = tool.generateNextQuestion("技术面试", null, null); assertTrue(r.getGuidance().contains("第1题")); assertTrue(r.getGuidance().contains("专业技能")); }
        @Test void secondQ() { NextQuestionResult r = tool.generateNextQuestion("Q1 xxx 技术面试", null, null); assertTrue(r.getGuidance().contains("第2题")); }
        @Test void fifthQ() { NextQuestionResult r = tool.generateNextQuestion("Q1 Q2 Q3 Q4 技术面试", null, null); assertTrue(r.getGuidance().contains("第5题")); }
    }

    @Nested @DisplayName("持久化")
    class PersistenceTests {
        @Test void withSession() {
            when(questionRepo.findBySessionIdOrderBySortOrder("s1")).thenReturn(List.of());
            InterviewSession s = new InterviewSession(); s.setSessionId("s1"); s.setQuestionCount(3);
            when(sessionRepo.findBySessionId("s1")).thenReturn(Optional.of(s));
            tool.generateNextQuestion("session=s1 Java", null, null);
            ArgumentCaptor<InterviewQuestion> c = ArgumentCaptor.forClass(InterviewQuestion.class);
            verify(questionRepo).save(c.capture());
            assertEquals("s1", c.getValue().getSessionId());
            assertEquals(0, c.getValue().getSortOrder());
            verify(sessionRepo).save(s);
            assertEquals(4, s.getQuestionCount());
        }
        @Test void withoutSession() { tool.generateNextQuestion("Java", null, null); verify(questionRepo, never()).save(any()); }
    }

    @Nested @DisplayName("模式感知")
    class ModeAwareTests {
        @Test void techDeep() { NextQuestionResult r = tool.generateNextQuestion("session=t1 Java", "TECH_DEEP", null); assertTrue(r.getGuidance().contains("追问") || r.getGuidance().contains("为什么")); assertEquals("TECH_DEEP", r.getMode()); }
        @Test void behavior() { NextQuestionResult r = tool.generateNextQuestion("session=t2 行为", "BEHAVIOR", null); assertTrue(r.getGuidance().contains("STAR")); assertEquals("BEHAVIOR", r.getMode()); }
        @Test void systemDesign() { NextQuestionResult r = tool.generateNextQuestion("session=t3 设计", "SYSTEM_DESIGN", null); assertTrue(r.getGuidance().contains("系统设计")); assertEquals("SYSTEM_DESIGN", r.getMode()); }
        @Test void pressure() { NextQuestionResult r = tool.generateNextQuestion("session=t4", "PRESSURE", null); assertTrue(r.getGuidance().contains("压力") || r.getGuidance().contains("质疑")); assertEquals("PRESSURE", r.getMode()); }
        @Test void defaultMode() { assertEquals("TECH_DEEP", tool.generateNextQuestion("session=t5 Java", null, null).getMode()); }
    }

    @Nested @DisplayName("简历参数")
    class ResumeTextTests {
        @Test void withResume() { NextQuestionResult r = tool.generateNextQuestion("session=t6 Java", "TECH_DEEP", "项目经验：电商秒杀\n负责模块：支付网关"); assertTrue(r.getGuidance().contains("项目")); }
        @Test void emptyResume() { NextQuestionResult r = tool.generateNextQuestion("session=t7 Java", "TECH_DEEP", ""); assertTrue(r.getGuidance().contains("技术原理") || r.getGuidance().contains("为什么")); }
        @Test void nonTechDeep() { NextQuestionResult r = tool.generateNextQuestion("session=t8", "BEHAVIOR", "项目经验：电商秒杀"); assertTrue(r.getGuidance().contains("STAR")); assertFalse(r.getGuidance().contains("电商秒杀")); }
    }
}
