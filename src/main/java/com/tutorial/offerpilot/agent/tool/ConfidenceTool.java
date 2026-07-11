/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.ConfidenceResult;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自信度分析工具 — 文本特征分析（口头禅密度、犹豫词频率、自我修正模式）。
 * 仅返回结构化数据 + 评估指导，自然语言评语由 LLM 生成。
 */
@Slf4j
@Component
public class ConfidenceTool {

    /** 常见犹豫/填充词 */
    private static final Set<String> HESITATION_WORDS = Set.of(
            "嗯", "呃", "那个", "就是", "然后", "这个",
            "怎么说呢", "emmm", "啊", "哦");

    /** 自我修正模式：否定/不确定/修正表达 */
    // TODO: 系数待通过 A/B 测试校准
    private static final Pattern SELF_CORRECTION = Pattern.compile(
            "不[,，]?(?:是|对|应该|准确[的来说])|其实|我觉得|可能|大概|也许|似乎|好像");

    @Tool(name = "analyze_confidence",
            description = "分析回答文本中的口头禅密度、犹豫词频率和自我修正模式，评估自信心水平")
    public ConfidenceResult analyze(
            @ToolParam(name = "text", description = "完整的用户回答文本") String text) {

        log.info("analyze_confidence called: textLen={}", text != null ? text.length() : 0);

        if (text == null || text.isBlank()) {
            return new ConfidenceResult("文本为空，无法分析", 0, Map.of(), 0, 0);
        }

        // 总字数（去除空白）
        String clean = text.replaceAll("\\s+", "");
        int totalChars = clean.length();

        // 1. 口头禅统计
        Map<String, Integer> fillerCounts = new LinkedHashMap<>();
        for (String word : HESITATION_WORDS) {
            int count = countOccurrences(text, word);
            if (count > 0) {
                fillerCounts.put(word, count);
            }
        }
        int totalFillers = fillerCounts.values().stream().mapToInt(Integer::intValue).sum();
        double fillerDensity = totalChars > 0 ? (double) totalFillers / totalChars * 1000 : 0;

        // 2. 自我修正统计
        Matcher matcher = SELF_CORRECTION.matcher(text);
        int correctionCount = 0;
        while (matcher.find()) {
            correctionCount++;
        }
        double correctionDensity = totalChars > 0 ? (double) correctionCount / totalChars * 1000 : 0;

        // 3. 计算自信度评分（0-100）
        // 口头禅密度低 + 自我修正少 = 自信度高
        int confidenceScore = calculateConfidenceScore(fillerDensity, correctionDensity, totalChars);

        // 4. 评估等级（仅给指导用，最终评语由 LLM 生成）
        String assessment = confidenceScore >= 80 ? "自信" : confidenceScore >= 60 ? "适度" : "偏弱";

        String guidance = String.format("""
                请根据以下自信度分析数据生成自然语言评语：
                - 自信度评分：%d/100（%s）
                - 口头禅密度：%.1f 次/千字
                - 高频口头禅：%s
                - 自我修正次数：%d 次
                请给出提升表达自信度的具体建议。""",
                confidenceScore, assessment, fillerDensity, fillerCounts, correctionCount);

        log.info("analyze_confidence: score={}, fillerDensity={}, correctionCount={}",
                confidenceScore, Math.round(fillerDensity * 10) / 10.0, correctionCount);

        return new ConfidenceResult(guidance, confidenceScore, fillerCounts,
                Math.round(fillerDensity * 10) / 10.0, correctionCount);
    }

    /** 统计 word 在 text 中的出现次数。 */
    private int countOccurrences(String text, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }
        return count;
    }

    /**
     * 计算自信度评分。
     * 基础分 100，按口头禅密度和自我修正密度扣分。
     * TODO: 系数待通过 A/B 测试校准。
     */
    private int calculateConfidenceScore(double fillerDensity, double correctionDensity, int totalChars) {
        int score = 100;
        score -= (int) Math.min(40, fillerDensity * 5);
        score -= (int) Math.min(30, correctionDensity * 10);
        if (totalChars < 50) {
            score -= 20;  // 样本不足，降信度
        }
        return Math.max(0, Math.min(100, score));
    }
}
