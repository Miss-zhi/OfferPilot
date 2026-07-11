/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.entity.StudyPlan;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.repository.StudyPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 学习计划管理服务。
 * 负责计划任务标记、优先级刷新、以及供 REST API 复用的优先级计算逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPlanService {

    private final StudyPlanRepository planRepo;
    private final KnowledgeMasteryRepository masteryRepo;
    private final SearchAnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    /**
     * 标记学习计划中的指定任务为完成。
     * 完成后重新计算进度，若全部完成则状态变为 COMPLETED。
     */
    @Transactional
    public StudyPlan completeTask(Long planId, int taskIndex) {
        StudyPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new BusinessException(404, "学习计划不存在"));

        try {
            List<Map<String, Object>> tasks = objectMapper.readValue(plan.getTasksJson(),
                    new TypeReference<List<Map<String, Object>>>() {});

            if (taskIndex < 0 || taskIndex >= tasks.size()) {
                throw new BusinessException(400, "任务索引超出范围");
            }

            Map<String, Object> task = tasks.get(taskIndex);
            task.put("completed", true);
            task.put("completedAt", LocalDateTime.now().toString());

            plan.setTasksJson(objectMapper.writeValueAsString(tasks));
            plan.setCompletedCount(plan.getCompletedCount() + 1);

            if (plan.getCompletedCount() >= plan.getTotalCount()) {
                plan.setStatus("COMPLETED");
            }

            plan.setLastUpdated(LocalDateTime.now());
            StudyPlan saved = planRepo.save(plan);

            log.info("Task completed: planId={}, taskIndex={}, progress={}/{}",
                    planId, taskIndex, saved.getCompletedCount(), saved.getTotalCount());
            return saved;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "任务更新失败: " + e.getMessage());
        }
    }

    /**
     * 根据最新面试数据刷新学习计划优先级。
     * 每次面试分析完成后由 LLM Agent 编排调用。
     * 对比现计划，补充新发现的薄弱点任务。
     */
    @Transactional
    public void refreshPlanPriorities(String userId) {
        List<KnowledgeMastery> masteries = masteryRepo.findByUserId(userId);
        List<StudyPlan> activePlans = planRepo.findByUserIdAndStatus(userId, "ACTIVE");

        // 获取新的薄弱点（掌握度 < 60）
        List<String> newWeaknesses = masteries.stream()
                .filter(m -> m.getScore() != null && m.getScore() < 60)
                .map(KnowledgeMastery::getKnowledgePoint)
                .toList();

        if (newWeaknesses.isEmpty() || activePlans.isEmpty()) {
            log.info("No new weaknesses to add for userId={}", userId);
            return;
        }

        List<StudyPlan> updatedPlans = new ArrayList<>();
        for (StudyPlan plan : activePlans) {
            try {
                List<Map<String, Object>> tasks = objectMapper.readValue(plan.getTasksJson(),
                        new TypeReference<List<Map<String, Object>>>() {});

                Set<String> existingTopics = new HashSet<>();
                for (Map<String, Object> task : tasks) {
                    Object topic = task.get("topic");
                    if (topic != null) {
                        existingTopics.add(topic.toString());
                    }
                }

                int added = 0;
                for (String weakness : newWeaknesses) {
                    if (!existingTopics.contains(weakness)) {
                        Map<String, Object> newTask = new LinkedHashMap<>();
                        newTask.put("topic", weakness);
                        newTask.put("description", "学习 " + weakness);
                        newTask.put("completed", false);
                        newTask.put("addedAt", LocalDateTime.now().toString());
                        tasks.add(newTask);
                        added++;
                    }
                }

                if (added > 0) {
                    plan.setTasksJson(objectMapper.writeValueAsString(tasks));
                    plan.setTotalCount(plan.getTotalCount() + added);
                    plan.setLastUpdated(LocalDateTime.now());
                    updatedPlans.add(plan);
                }
            } catch (Exception e) {
                log.error("Failed to refresh plan priorities for planId={}: {}",
                        plan.getId(), e.getMessage());
            }
        }

        if (!updatedPlans.isEmpty()) {
            planRepo.saveAll(updatedPlans);
            log.info("Refreshed {} plans for userId={}, added {} new weaknesses",
                    updatedPlans.size(), userId, newWeaknesses.size());
        }
    }

    /**
     * 计算用户知识点的优先级排序，供 REST API 直接调用。
     * 复用与 PriorityRankTool 相同的排序逻辑。
     */
    public Map<String, Object> calculatePriorities(String userId) {
        List<KnowledgeMastery> masteries = masteryRepo.findByUserId(userId);
        Map<String, Integer> frequencyMap = analyticsService.getTopicFrequency();

        List<Map<String, Object>> rankedList = new ArrayList<>();
        for (KnowledgeMastery m : masteries) {
            String topic = m.getKnowledgePoint();
            int score = m.getScore() != null ? m.getScore() : 0;
            int frequency = frequencyMap.getOrDefault(topic, 1);
            int priority = frequency * (100 - score);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("topic", topic);
            item.put("currentScore", score);
            item.put("frequency", frequency);
            item.put("priority", priority);

            Integer previousScore = m.getPreviousScore();
            if (previousScore != null) {
                item.put("previousScore", previousScore);
                item.put("trend", score > previousScore ? "up" : score < previousScore ? "down" : "stable");
            } else {
                item.put("trend", "stable");
            }
            rankedList.add(item);
        }

        rankedList.sort((a, b) -> Integer.compare(
                (Integer) b.get("priority"), (Integer) a.get("priority")));

        // 本周学习计划汇总
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEnd = LocalDate.now().with(DayOfWeek.SUNDAY);
        List<StudyPlan> plans = planRepo.findByUserIdAndStatus(userId, "ACTIVE");
        int completed = plans.stream()
                .mapToInt(p -> p.getCompletedCount() != null ? p.getCompletedCount() : 0)
                .sum();
        int total = plans.stream()
                .mapToInt(p -> p.getTotalCount() != null ? p.getTotalCount() : 0)
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weekStart", weekStart.toString());
        result.put("weekEnd", weekEnd.toString());
        result.put("completedTasks", completed);
        result.put("totalTasks", total);
        result.put("rankedWeaknesses", rankedList);
        return result;
    }
}
