/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.PriorityResult;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.service.SearchAnalyticsService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 优先级排序工具。
 * 按 高频考点 × 低掌握度 对薄弱知识点进行量化优先级排序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriorityRankTool {

    private final KnowledgeMasteryRepository masteryRepo;
    private final SearchAnalyticsService analyticsService;

    @Tool(name = "prioritize_weaknesses",
            description = "按高频考点 × 低掌握度对薄弱知识点进行量化优先级排序，输出排序列表和LLM指导")
    public PriorityResult prioritize(
            @ToolParam(name = "user_id", description = "用户ID") String userId) {

        log.info("prioritize_weaknesses called: userId={}", userId);

        // 1. 获取用户各知识点掌握度
        List<KnowledgeMastery> masteries = masteryRepo.findByUserId(userId);

        // 2. 获取知识点考频（从搜索日志统计各知识点出现次数）
        Map<String, Integer> frequencyMap = analyticsService.getTopicFrequency();

        // 3. 计算优先级分数并构建排序列表
        List<PriorityResult.RankedItem> items = new ArrayList<>();
        for (KnowledgeMastery m : masteries) {
            String topic = m.getKnowledgePoint();
            int score = m.getScore() != null ? m.getScore() : 0;
            int frequency = frequencyMap.getOrDefault(topic, 1);

            // 优先级 = 考频权重 × (100 - 掌握度)
            int priority = frequency * (100 - score);

            PriorityResult.RankedItem item = new PriorityResult.RankedItem();
            item.setTopic(topic);
            item.setCurrentScore(score);
            item.setFrequency(frequency);
            item.setPriority(priority);
            item.setUrgency(priority >= 5000 ? "HIGH" : priority >= 2000 ? "MEDIUM" : "LOW");
            items.add(item);
        }

        // 按优先级降序
        items.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // 4. 构建 LLM 指导
        String guidance = buildPriorityGuidance(items);

        log.info("prioritize_weaknesses result: topics={}, topPriority={}",
                items.size(), items.isEmpty() ? "N/A" : items.get(0).getTopic());
        return new PriorityResult(guidance, items);
    }

    /**
     * 构建优先级指导文本，以 Markdown 表格呈现排序结果。
     * 仅提供结构化数据，不拼接自然语言成品文本。
     */
    private String buildPriorityGuidance(List<PriorityResult.RankedItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下优先级排序生成学习计划建议：\n");
        sb.append("排序规则：高频考点 × 低掌握度 = 最优先\n\n");
        sb.append("| 优先级 | 知识点 | 当前掌握度 | 考频 | 紧急度 |\n");
        sb.append("|--------|--------|-----------|------|--------|\n");
        int rank = 1;
        for (PriorityResult.RankedItem item : items) {
            sb.append(String.format("| %d | %s | %d | %d | %s |\n",
                    rank++, item.getTopic(), item.getCurrentScore(),
                    item.getFrequency(), item.getUrgency()));
        }
        sb.append("\n请按优先级从高到低安排每日学习任务，每天1-2个知识点，1-2小时为宜。");
        return sb.toString();
    }
}
