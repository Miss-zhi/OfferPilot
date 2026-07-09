/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.QuestionSearchResult;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionSearchTool {

    private final KnowledgeBaseService kbService;

    @Tool(name = "search_questions", description = "搜索面试题库，根据关键词检索相关面试题目")
    public QuestionSearchResult searchQuestions(
            @ToolParam(name = "keyword", description = "搜索关键词，如岗位名称或技能") String keyword) {
        log.info("search_questions called: keyword={}", keyword);
        return kbService.searchQuestions(keyword);
    }
}
