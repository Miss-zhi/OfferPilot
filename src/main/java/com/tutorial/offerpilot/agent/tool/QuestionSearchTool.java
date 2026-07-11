/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.QuestionSearchResult;
import com.tutorial.offerpilot.dto.tool.SearchRequest;
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
            @ToolParam(name = "keyword", description = "搜索关键词，如岗位名称或技能") String keyword,
            @ToolParam(name = "category", description = "分类过滤（可选）：专业技能/项目经验/情景分析/行为面试/职业规划", required = false) String category,
            @ToolParam(name = "difficulty", description = "难度过滤（可选）：easy/medium/hard", required = false) String difficulty,
            @ToolParam(name = "position", description = "岗位名称过滤（可选）", required = false) String position,
            @ToolParam(name = "top_k", description = "返回数量（可选），默认10", required = false) Integer topK) {
        log.info("search_questions called: keyword={}, category={}, difficulty={}", keyword, category, difficulty);
        SearchRequest req = new SearchRequest();
        req.setKeywords(keyword);
        req.setCategory(category);
        req.setDifficulty(difficulty);
        req.setPosition(position);
        req.setTopK(topK != null ? topK : 10);
        return kbService.searchQuestions(req);
    }
}
