/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.entity.StudyPlan;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.repository.StudyPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LearningPlanService 单元测试")
class LearningPlanServiceTest {

    @Mock
    private StudyPlanRepository planRepo;

    @Mock
    private KnowledgeMasteryRepository masteryRepo;

    @Mock
    private SearchAnalyticsService analyticsService;

    private LearningPlanService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new LearningPlanService(planRepo, masteryRepo, analyticsService, objectMapper);
    }

    // ==================== completeTask ====================

    @Nested
    @DisplayName("completeTask - 任务完成标记")
    class CompleteTaskTests {

        @Test
        @DisplayName("标记任务完成 → 更新 completedCount + lastUpdated")
        void completeTask_shouldUpdateProgress() throws Exception {
            String tasksJson = "[{\"topic\":\"算法\",\"completed\":false}]";
            StudyPlan plan = createPlan(1L, tasksJson, 0, 1);
            when(planRepo.findById(1L)).thenReturn(Optional.of(plan));
            when(planRepo.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

            StudyPlan result = service.completeTask(1L, 0);

            assertEquals(1, result.getCompletedCount());
            assertNotNull(result.getLastUpdated());
            verify(planRepo).save(any(StudyPlan.class));
        }

        @Test
        @DisplayName("全部任务完成 → 状态变为 COMPLETED")
        void allTasksCompleted_shouldSetStatusToCompleted() throws Exception {
            String tasksJson = "[{\"topic\":\"算法\",\"completed\":false}]";
            StudyPlan plan = createPlan(1L, tasksJson, 0, 1);
            when(planRepo.findById(1L)).thenReturn(Optional.of(plan));
            when(planRepo.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

            StudyPlan result = service.completeTask(1L, 0);

            assertEquals("COMPLETED", result.getStatus());
        }

        @Test
        @DisplayName("部分完成 → 状态保持 ACTIVE")
        void partialComplete_shouldKeepActive() throws Exception {
            String tasksJson = "[{\"topic\":\"算法\",\"completed\":false},{\"topic\":\"设计\",\"completed\":false}]";
            StudyPlan plan = createPlan(1L, tasksJson, 0, 2);
            when(planRepo.findById(1L)).thenReturn(Optional.of(plan));
            when(planRepo.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));

            StudyPlan result = service.completeTask(1L, 0);

            assertEquals("ACTIVE", result.getStatus());
            assertEquals(1, result.getCompletedCount());
        }

        @Test
        @DisplayName("任务索引超出范围 → 抛出异常")
        void outOfRangeIndex_shouldThrowException() throws Exception {
            String tasksJson = "[{\"topic\":\"算法\",\"completed\":false}]";
            StudyPlan plan = createPlan(1L, tasksJson, 0, 1);
            when(planRepo.findById(1L)).thenReturn(Optional.of(plan));

            assertThrows(BusinessException.class, () -> service.completeTask(1L, 5));
        }

        @Test
        @DisplayName("计划不存在 → 抛出 BusinessException(404)")
        void planNotFound_shouldThrow404() {
            when(planRepo.findById(999L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.completeTask(999L, 0));
            assertEquals(404, ex.getErrorCode());
        }
    }

    // ==================== refreshPlanPriorities ====================

    @Nested
    @DisplayName("refreshPlanPriorities - 计划优先级刷新")
    class RefreshPlanPrioritiesTests {

        @Test
        @DisplayName("新增薄弱点 → 追加到活跃计划")
        void newWeaknesses_shouldAppendToActivePlan() throws Exception {
            String tasksJson = "[{\"topic\":\"算法\",\"completed\":false}]";
            StudyPlan plan = createPlan(1L, tasksJson, 0, 1);
            when(planRepo.findByUserIdAndStatus("user1", "ACTIVE")).thenReturn(List.of(plan));

            KnowledgeMastery m = new KnowledgeMastery();
            m.setKnowledgePoint("系统设计");
            m.setScore(30);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(m));
            when(planRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            service.refreshPlanPriorities("user1");

            verify(planRepo).saveAll(anyList());
        }

        @Test
        @DisplayName("无活跃计划 → 不执行任何操作")
        void noActivePlans_shouldDoNothing() {
            when(planRepo.findByUserIdAndStatus("user1", "ACTIVE")).thenReturn(List.of());
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of());

            service.refreshPlanPriorities("user1");

            verify(planRepo, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("掌握度 >= 60 的不视为薄弱点")
        void highMastery_shouldNotBeAdded() {
            String tasksJson = "[{\"topic\":\"算法\",\"completed\":false}]";
            StudyPlan plan = createPlan(1L, tasksJson, 0, 1);
            when(planRepo.findByUserIdAndStatus("user1", "ACTIVE")).thenReturn(List.of(plan));

            KnowledgeMastery m = new KnowledgeMastery();
            m.setKnowledgePoint("系统设计");
            m.setScore(80);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(m));

            service.refreshPlanPriorities("user1");

            // 80 >= 60，不应追加，saveAll 不被调用
            verify(planRepo, never()).saveAll(anyList());
        }
    }

    // ==================== calculatePriorities ====================

    @Nested
    @DisplayName("calculatePriorities - REST API 优先级计算")
    class CalculatePrioritiesTests {

        @Test
        @DisplayName("返回按优先级降序排列的结果")
        void shouldReturnRankedList() {
            KnowledgeMastery a = createMastery("算法", 30);
            KnowledgeMastery b = createMastery("设计", 80);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(a, b));

            Map<String, Integer> freq = new LinkedHashMap<>();
            freq.put("算法", 10);
            freq.put("设计", 5);
            when(analyticsService.getTopicFrequency()).thenReturn(freq);
            when(planRepo.findByUserIdAndStatus("user1", "ACTIVE")).thenReturn(List.of());

            Map<String, Object> result = service.calculatePriorities("user1");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ranked = (List<Map<String, Object>>) result.get("rankedWeaknesses");
            assertEquals(2, ranked.size());
            assertEquals("算法", ranked.get(0).get("topic"));
            assertEquals(700, ranked.get(0).get("priority")); // 10*(100-30)
            assertEquals("设计", ranked.get(1).get("topic"));
            assertEquals(100, ranked.get(1).get("priority")); // 5*(100-80)
        }

        @Test
        @DisplayName("空知识点 → 返回空列表")
        void emptyMasteries_shouldReturnEmptyList() {
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of());
            when(analyticsService.getTopicFrequency()).thenReturn(Map.of());
            when(planRepo.findByUserIdAndStatus("user1", "ACTIVE")).thenReturn(List.of());

            Map<String, Object> result = service.calculatePriorities("user1");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ranked = (List<Map<String, Object>>) result.get("rankedWeaknesses");
            assertTrue(ranked.isEmpty());
            assertEquals(0, result.get("completedTasks"));
            assertEquals(0, result.get("totalTasks"));
        }

        @Test
        @DisplayName("包含 previousScore → 输出 trend")
        void withPreviousScore_shouldOutputTrend() {
            KnowledgeMastery a = createMastery("Python", 70);
            a.setPreviousScore(50);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(a));
            when(analyticsService.getTopicFrequency()).thenReturn(Map.of());
            when(planRepo.findByUserIdAndStatus("user1", "ACTIVE")).thenReturn(List.of());

            Map<String, Object> result = service.calculatePriorities("user1");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ranked = (List<Map<String, Object>>) result.get("rankedWeaknesses");
            assertEquals("up", ranked.get(0).get("trend"));
        }
    }

    // ======================== 辅助方法 ========================

    private StudyPlan createPlan(Long id, String tasksJson, int completed, int total) {
        StudyPlan plan = new StudyPlan();
        plan.setId(id);
        plan.setUserId("user1");
        plan.setTasksJson(tasksJson);
        plan.setCompletedCount(completed);
        plan.setTotalCount(total);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private KnowledgeMastery createMastery(String knowledgePoint, Integer score) {
        KnowledgeMastery m = new KnowledgeMastery();
        m.setUserId("user1");
        m.setKnowledgePoint(knowledgePoint);
        m.setScore(score);
        return m;
    }
}
