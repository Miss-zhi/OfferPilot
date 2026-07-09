/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.ResumeParseResult;
import com.tutorial.offerpilot.service.ResumeService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeParseTool {

    private final ResumeService resumeService;

    @Tool(name = "parse_resume", description = "解析简历文件（PDF/DOCX），提取姓名、工作经历、技能等结构化信息")
    public ResumeParseResult parse(
            @ToolParam(name = "pdf_url", description = "简历文件的URL地址") String pdfUrl) {
        log.info("parse_resume called: url={}", pdfUrl);
        return resumeService.parseResume(pdfUrl);
    }
}
