/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.ResourceListResult;
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
public class ResourceSearchTool {

    private final KnowledgeBaseService kbService;

    @Tool(name = "search_resources", description = "搜索学习资源，包括教程、文档、视频等面试准备材料")
    public ResourceListResult searchResources(
            @ToolParam(name = "topic", description = "学习主题，如算法、系统设计等") String topic,
            @ToolParam(name = "category", description = "分类过滤（可选）", required = false) String category,
            @ToolParam(name = "difficulty", description = "难度过滤（可选）：easy/medium/hard", required = false) String difficulty,
            @ToolParam(name = "top_k", description = "返回数量（可选），默认10", required = false) Integer topK) {
        log.info("search_resources called: topic={}", topic);
        SearchRequest req = new SearchRequest();
        req.setKeywords(topic);
        req.setCategory(category);
        req.setDifficulty(difficulty);
        req.setTopK(topK != null ? topK : 10);
        return kbService.searchResources(req);
    }
}
