/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.dto.progress.ProgressResponse;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.entity.StudyPlan;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.repository.StudyPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProgressController Web 层测试")
class ProgressControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InterviewSessionRepository sessionRepo;

    @Mock
    private KnowledgeMasteryRepository masteryRepo;

    @Mock
    private StudyPlanRepository planRepo;

    @InjectMocks
    private ProgressController controller;

    private static final String USER_ID = "testuser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        // 设置 SecurityContext，使 @AuthenticationPrincipal 能正确解析 UserDetails
        User user = new User(USER_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== GET /api/v1/offerpilot/progress ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/progress")
    class GetProgressTests {

        @Test
        @DisplayName("默认参数 → 返回进度数据含面试次数")
        void defaultRange_shouldReturnProgress() throws Exception {
            when(sessionRepo.countByUserId(USER_ID)).thenReturn(5L);
            when(masteryRepo.findByUserId(USER_ID)).thenReturn(List.of());
            when(planRepo.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/offerpilot/progress"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value("month"))
                    .andExpect(jsonPath("$.data.interviewCount").value(5))
                    .andExpect(jsonPath("$.data.scoreTrend").isArray())
                    .andExpect(jsonPath("$.data.knowledgeMastery").isMap())
                    .andExpect(jsonPath("$.data.studyPlan.completed").value(0))
                    .andExpect(jsonPath("$.data.studyPlan.total").value(0));
        }

        @Test
        @DisplayName("指定 range=week → 返回 period=week")
        void customRange_shouldReflectInResponse() throws Exception {
            when(sessionRepo.countByUserId(USER_ID)).thenReturn(3L);
            when(masteryRepo.findByUserId(USER_ID)).thenReturn(List.of());
            when(planRepo.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/offerpilot/progress").param("range", "week"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value("week"))
                    .andExpect(jsonPath("$.data.interviewCount").value(3));
        }

        @Test
        @DisplayName("有知识点掌握数据 → 返回 scoreTrend 和 masteryMap")
        void withMasteryData_shouldReturnTrends() throws Exception {
            KnowledgeMastery m1 = new KnowledgeMastery();
            m1.setKnowledgePoint("Java基础");
            m1.setPreviousScore(60);
            m1.setScore(75);

            KnowledgeMastery m2 = new KnowledgeMastery();
            m2.setKnowledgePoint("数据库");
            m2.setPreviousScore(80);
            m2.setScore(70);

            when(sessionRepo.countByUserId(USER_ID)).thenReturn(2L);
            when(masteryRepo.findByUserId(USER_ID)).thenReturn(List.of(m1, m2));
            when(planRepo.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/offerpilot/progress"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scoreTrend.length()").value(4))
                    .andExpect(jsonPath("$.data.scoreTrend[0]").value(60))
                    .andExpect(jsonPath("$.data.scoreTrend[1]").value(75))
                    .andExpect(jsonPath("$.data.scoreTrend[2]").value(80))
                    .andExpect(jsonPath("$.data.scoreTrend[3]").value(70))
                    .andExpect(jsonPath("$.data.knowledgeMastery['Java基础'].first").value(60))
                    .andExpect(jsonPath("$.data.knowledgeMastery['Java基础'].current").value(75))
                    .andExpect(jsonPath("$.data.knowledgeMastery['Java基础'].trend").value("up"))
                    .andExpect(jsonPath("$.data.knowledgeMastery['数据库'].first").value(80))
                    .andExpect(jsonPath("$.data.knowledgeMastery['数据库'].current").value(70))
                    .andExpect(jsonPath("$.data.knowledgeMastery['数据库'].trend").value("down"));
        }

        @Test
        @DisplayName("分数不变 → trend=stable")
        void stableScore_shouldReturnStable() throws Exception {
            KnowledgeMastery m = new KnowledgeMastery();
            m.setKnowledgePoint("算法");
            m.setPreviousScore(70);
            m.setScore(70);

            when(sessionRepo.countByUserId(USER_ID)).thenReturn(1L);
            when(masteryRepo.findByUserId(USER_ID)).thenReturn(List.of(m));
            when(planRepo.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/offerpilot/progress"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.knowledgeMastery['算法'].trend").value("stable"));
        }

        @Test
        @DisplayName("有学习计划 → 返回 completed/total")
        void withStudyPlan_shouldReturnPlanInfo() throws Exception {
            StudyPlan plan1 = new StudyPlan();
            plan1.setCompletedCount(3);
            plan1.setTotalCount(10);

            StudyPlan plan2 = new StudyPlan();
            plan2.setCompletedCount(5);
            plan2.setTotalCount(8);

            when(sessionRepo.countByUserId(USER_ID)).thenReturn(0L);
            when(masteryRepo.findByUserId(USER_ID)).thenReturn(List.of());
            when(planRepo.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(plan1, plan2));

            mockMvc.perform(get("/api/v1/offerpilot/progress"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.studyPlan.completed").value(8))
                    .andExpect(jsonPath("$.data.studyPlan.total").value(18));
        }

        @Test
        @DisplayName("认证用户 → 200 OK")
        void authenticated_shouldReturn200() throws Exception {
            when(sessionRepo.countByUserId(USER_ID)).thenReturn(0L);
            when(masteryRepo.findByUserId(USER_ID)).thenReturn(List.of());
            when(planRepo.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/offerpilot/progress"))
                    .andExpect(status().isOk());
        }
    }
}
