/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.QualityCheckResult;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历质量专项检查工具 — 三项检查：技能层次分类、量化数据覆盖度、技术栈罗列 vs 业务成果。
 * 工具只收集原始数据和基础统计，判断交给 LLM。
 */
@Slf4j
@Component
public class ResumeQualityTool {

    private static final Pattern SKILL_PATTERN = Pattern.compile(
            "(?:技能|技术栈|技术能力|专业技能)[：:]*([^。\\n]*(?:[。\\n][^。\\n]*){0,5})");
    private static final Pattern EXP_SECTION_PATTERN = Pattern.compile(
            "(?:项目经历|工作经历|实习经历)[：:]*([\\s\\S]*?)(?=教育背景|技能|证书|自我评价|$)");
    private static final Pattern QUANTIFIED_PATTERN = Pattern.compile(
            "\\d+[%％倍万千亿kKwWmM]");

    @Tool(name = "check_resume_quality",
            description = "专项检查简历的3类常见问题：技能层次分类、量化数据覆盖度、技术栈罗列vs业务成果")
    public QualityCheckResult checkResumeQuality(
            @ToolParam(name = "resume_text", description = "简历文本内容") String resumeText) {
        log.info("check_resume_quality called: textLen={}",
                resumeText != null ? resumeText.length() : 0);

        if (resumeText == null || resumeText.isBlank()) {
            return new QualityCheckResult("简历文本为空，无法进行质量检查。", List.of(), 0);
        }

        List<QualityCheckResult.RawData> rawDataList = new ArrayList<>();

        Matcher skillMatcher = SKILL_PATTERN.matcher(resumeText);
        String skillSection = skillMatcher.find() ? skillMatcher.group(1).trim() : "未检测到技能段落";
        rawDataList.add(new QualityCheckResult.RawData("技能段落", skillSection, null));

        Matcher expMatcher = EXP_SECTION_PATTERN.matcher(resumeText);
        String expSection = expMatcher.find() ? expMatcher.group(1).trim() : "未检测到项目经历段落";
        String[] expLines = expSection.split("[。\\n]");
        long quantifiedCount = 0;
        for (String line : expLines) {
            if (QUANTIFIED_PATTERN.matcher(line).find()) {
                quantifiedCount++;
            }
        }
        int totalExpCount = Math.max(expLines.length, 1);
        String stats = String.format("共 %d 段描述，其中 %d 段包含量化数据，占比 %.0f%%",
                totalExpCount, quantifiedCount, 100.0 * quantifiedCount / totalExpCount);
        rawDataList.add(new QualityCheckResult.RawData("项目经历段落", expSection, stats));

        String guidance = buildGuidance(rawDataList);
        log.info("check_resume_quality result: sections={}, quantified={}/{}",
                rawDataList.size(), quantifiedCount, totalExpCount);
        return new QualityCheckResult(guidance, rawDataList, 0);
    }

    private String buildGuidance(List<QualityCheckResult.RawData> rawData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下简历进行三项专项检查，逐项给出发现的问题和改进建议：\n\n");

        for (QualityCheckResult.RawData data : rawData) {
            sb.append("【检查项：").append(data.getSection()).append("】\n");
            sb.append("原文：").append(data.getContent()).append("\n");
            if (data.getStats() != null) {
                sb.append("统计：").append(data.getStats()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("请从以下三个维度评估：\n");
        sb.append("1. 技能层次分类：技能是否按精通/熟悉/了解分类组织？\n");
        sb.append("2. 量化数据覆盖度：是否有足够的数据支撑成果描述？建议每段经历至少包含 1-2 个量化指标。\n");
        sb.append("3. 技术栈 vs 业务成果：是否在罗列技术栈，还是在描述使用技术解决的具体业务问题？\n");
        return sb.toString();
    }
}
