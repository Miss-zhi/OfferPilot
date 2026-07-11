/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.StarCheckResult;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STAR 检查工具 — 对简历中每段项目/工作经历进行 S/T/A/R 四要素完整性检测。
 * 工具只做文本分段，STAR 判断完全交给 LLM。
 */
@Slf4j
@Component
public class StarCheckTool {

    private static final Pattern EXP_PATTERN = Pattern.compile(
            "(?:项目经历|工作经历|实习经历)[：:]*\\s*((?:(?!项目经历|工作经历|实习经历|教育背景|技能).)+)",
            Pattern.DOTALL);

    @Tool(name = "check_star",
            description = "检查简历中每段项目/工作经历的STAR四要素（情境-任务-行动-结果）完整性，返回分段文本和LLM指导")
    public StarCheckResult checkStar(
            @ToolParam(name = "resume_text", description = "简历文本内容") String resumeText) {
        log.info("check_star called: textLen={}", resumeText != null ? resumeText.length() : 0);

        if (resumeText == null || resumeText.isBlank()) {
            return new StarCheckResult("简历文本为空，无法进行 STAR 检查。", List.of(), 0);
        }

        List<StarCheckResult.StarItem> items = new ArrayList<>();
        Matcher matcher = EXP_PATTERN.matcher(resumeText);
        int index = 1;
        while (matcher.find() && index <= 20) {
            String content = matcher.group(1).trim();
            if (content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            items.add(new StarCheckResult.StarItem(index, content));
            index++;
        }

        String guidance = buildGuidance(items, resumeText);
        log.info("check_star result: items={}", items.size());
        return new StarCheckResult(guidance, items, items.size());
    }

    private String buildGuidance(List<StarCheckResult.StarItem> items, String resumeText) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下简历经历的 STAR 四要素完整性进行评估：\n\n");
        sb.append("【STAR 四要素】\n");
        sb.append("- S (情境)：当时的背景和目标是什么？\n");
        sb.append("- T (任务)：需要完成的具体任务是什么？\n");
        sb.append("- A (行动)：采取了哪些具体行动？\n");
        sb.append("- R (结果)：取得了什么可量化的成果？\n\n");

        sb.append("对每段经历，请判断 S/T/A/R 四个要素是否完整：\n\n");
        for (StarCheckResult.StarItem item : items) {
            sb.append("--- 经历 ").append(item.getIndex()).append(" ---\n");
            sb.append(item.getContent()).append("\n");
            sb.append("[评价：缺失S/T/A/R中哪些要素？如何改进？]\n\n");
        }
        sb.append("请逐段给出改进建议，帮助候选人用 STAR 方法重写经历。");
        return sb.toString();
    }
}
