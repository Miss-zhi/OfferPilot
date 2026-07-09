/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.ResumeEvaluateResult;
import com.tutorial.offerpilot.service.ResumeService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeEvaluateTool {

    private final ResumeService resumeService;

    @Tool(name = "evaluate_resume", description = "评估简历质量并给出改进建议，包括格式、内容、关键词优化")
    public ResumeEvaluateResult evaluate(
            @ToolParam(name = "resume_text", description = "简历文本内容") String resumeText) {
        log.info("evaluate_resume called");
        return resumeService.evaluateResume(resumeText);
    }
}
