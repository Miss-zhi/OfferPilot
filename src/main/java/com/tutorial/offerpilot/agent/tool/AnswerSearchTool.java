/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.AnswerSearchResult;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerSearchTool {

    private final KnowledgeBaseService kbService;

    @Tool(name = "search_answers", description = "搜索优秀面试答案库，根据关键词检索高质量回答范例")
    public AnswerSearchResult searchAnswers(
            @ToolParam(name = "keyword", description = "搜索关键词") String keyword) {
        log.info("search_answers called: keyword={}", keyword);
        return kbService.searchAnswers(keyword);
    }
}
