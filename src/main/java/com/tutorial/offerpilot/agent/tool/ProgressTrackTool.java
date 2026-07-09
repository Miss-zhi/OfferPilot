/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.ProgressResult;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.entity.StudyPlan;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.repository.StudyPlanRepository;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProgressTrackTool {

    private final InterviewSessionRepository sessionRepo;
    private final KnowledgeMasteryRepository masteryRepo;
    private final StudyPlanRepository planRepo;

    @Tool(name = "track_progress", description = "追踪用户的学习和面试准备进度，返回各模块完成情况")
    public ProgressResult trackProgress(
            @ToolParam(name = "user_id", description = "用户ID") String userId) {
        log.info("track_progress called: userId={}", userId);

        long interviewCount = sessionRepo.countByUserId(userId);

        List<KnowledgeMastery> masteries = masteryRepo.findByUserId(userId);
        int averageScore = masteries.isEmpty() ? 0
                : (int) masteries.stream()
                        .filter(m -> m.getScore() != null)
                        .mapToInt(KnowledgeMastery::getScore)
                        .average()
                        .orElse(0);

        Map<String, Integer> knowledgeScores = new LinkedHashMap<>();
        for (KnowledgeMastery m : masteries) {
            if (m.getScore() != null) {
                knowledgeScores.put(m.getKnowledgePoint(), m.getScore());
            }
        }

        List<StudyPlan> plans = planRepo.findByUserIdAndStatus(userId, "ACTIVE");
        int completedTasks = plans.stream().mapToInt(p -> p.getCompletedCount() != null ? p.getCompletedCount() : 0).sum();
        int totalTasks = plans.stream().mapToInt(p -> p.getTotalCount() != null ? p.getTotalCount() : 0).sum();

        String guidance = buildGuidance(interviewCount, averageScore, completedTasks, totalTasks, knowledgeScores);

        log.info("track_progress result: interviewCount={}, avgScore={}, plans={}",
                interviewCount, averageScore, plans.size());
        return new ProgressResult(interviewCount, averageScore, knowledgeScores, guidance, completedTasks, totalTasks);
    }

    /**
     * 构建进度汇总指导，由 LLM 据此生成自然语言总结。
     * 不拼接成品文本，仅提供结构化上下文。
     */
    private String buildGuidance(long interviewCount, int averageScore, int completed, int total,
                                  Map<String, Integer> knowledgeScores) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下学习进度数据生成一段简洁的进度总结：\n");
        sb.append("- 面试练习次数：").append(interviewCount).append(" 次\n");
        if (averageScore > 0) {
            sb.append("- 平均得分：").append(averageScore).append(" 分\n");
        }
        if (total > 0) {
            sb.append("- 学习计划完成度：").append(completed).append("/").append(total).append("\n");
        }
        if (!knowledgeScores.isEmpty()) {
            sb.append("- 各知识点掌握度：\n");
            knowledgeScores.forEach((k, v) -> sb.append("  · ").append(k).append("：").append(v).append(" 分\n"));
        }
        sb.append("请用鼓励的语气输出，突出进步趋势和待加强的薄弱点。");
        return sb.toString();
    }
}
