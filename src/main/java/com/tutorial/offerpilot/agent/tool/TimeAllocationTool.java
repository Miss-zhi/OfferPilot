/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorial.offerpilot.dto.tool.TimeAllocationResult;
import com.tutorial.offerpilot.exception.BusinessException;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 时间分配分析工具 — 根据回答文本长度估算时长，判断合理性并返回评估枚举值。
 * 仅返回结构化数据 + 评估指导，自然语言评语由 LLM 生成。
 */
@Slf4j
@Component
public class TimeAllocationTool {

    /** 中文正常语速约 250 字/分钟 */
    private static final int CHARS_PER_MINUTE = 250;
    /** 合理回答时长范围（秒） */
    private static final int MIN_SECONDS = 30;
    private static final int MAX_SECONDS = 180;
    private static final int IDEAL_MIN = 60;
    private static final int IDEAL_MAX = 120;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = "analyze_time_allocation",
            description = "根据回答文本长度估算回答时长，判断是否合理（太短/太长/适中）")
    public TimeAllocationResult analyze(
            @ToolParam(name = "answers",
                    description = "各题回答文本的JSON数组，每项{question, answer}") String answersJson) {

        log.info("analyze_time_allocation called");

        if (answersJson == null || answersJson.isBlank()) {
            return new TimeAllocationResult("回答数据为空，无法分析时间分配",
                    List.of(), 0, 0, 0);
        }

        // 解析 JSON 数组
        List<Map<String, String>> qaList;
        try {
            qaList = objectMapper.readValue(answersJson,
                    new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse answers JSON: {}", e.getMessage());
            throw new BusinessException(400, "answers JSON 格式错误: " + e.getMessage());
        }

        List<TimeAllocationResult.TimeItem> items = new ArrayList<>();

        for (Map<String, String> qa : qaList) {
            String question = qa.getOrDefault("question", "");
            String answer = qa.getOrDefault("answer", "");

            int chars = answer.replaceAll("\\s+", "").length();
            int estimatedSeconds = chars > 0 ? chars * 60 / CHARS_PER_MINUTE : 0;

            TimeAllocationResult.TimeItem item = new TimeAllocationResult.TimeItem();
            item.setQuestion(question.length() > 50
                    ? question.substring(0, 50) : question);
            item.setCharCount(chars);
            item.setEstimatedSeconds(estimatedSeconds);

            // 仅返回评估枚举值，不输出自然语言评语（评语由 LLM 生成）
            if (estimatedSeconds < MIN_SECONDS) {
                item.setAssessment("TOO_SHORT");
            } else if (estimatedSeconds > MAX_SECONDS) {
                item.setAssessment("TOO_LONG");
            } else if (estimatedSeconds < IDEAL_MIN) {
                item.setAssessment("ACCEPTABLE");
            } else if (estimatedSeconds <= IDEAL_MAX) {
                item.setAssessment("GOOD");
            } else {
                item.setAssessment("ACCEPTABLE");
            }
            items.add(item);
        }

        // 整体统计
        int totalSeconds = items.stream()
                .mapToInt(TimeAllocationResult.TimeItem::getEstimatedSeconds).sum();
        long tooShort = items.stream()
                .filter(i -> "TOO_SHORT".equals(i.getAssessment())).count();
        long tooLong = items.stream()
                .filter(i -> "TOO_LONG".equals(i.getAssessment())).count();

        String guidance = String.format("""
                请根据以下时间分配数据生成自然语言分析：
                总计 %d 秒，%d 题过短（TOO_SHORT），%d 题过长（TOO_LONG）
                评估标准：GOOD=时长适中（60-120秒/题），ACCEPTABLE=可接受，TOO_SHORT=回答过短缺乏深度，TOO_LONG=回答过长需收敛
                请为每道题生成具体的改进建议。""",
                totalSeconds, tooShort, tooLong);

        log.info("analyze_time_allocation: {} items, totalSeconds={}, tooShort={}, tooLong={}",
                items.size(), totalSeconds, tooShort, tooLong);

        return new TimeAllocationResult(guidance, items,
                totalSeconds, (int) tooShort, (int) tooLong);
    }
}
