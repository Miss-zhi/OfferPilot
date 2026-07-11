/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.CompanySearchResult;
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
public class CompanySearchTool {

    private final KnowledgeBaseService kbService;

    @Tool(name = "search_company_interviews", description = "搜索指定公司的面试经验和面经，帮助用户了解目标公司的面试风格")
    public CompanySearchResult searchCompanyInterviews(
            @ToolParam(name = "company_name", description = "公司名称") String companyName,
            @ToolParam(name = "position", description = "岗位名称过滤（可选）", required = false) String position,
            @ToolParam(name = "top_k", description = "返回数量（可选），默认10", required = false) Integer topK) {
        log.info("search_company_interviews called: company={}", companyName);
        SearchRequest req = new SearchRequest();
        req.setKeywords(companyName);
        req.setCompany(companyName);
        req.setPosition(position);
        req.setTopK(topK != null ? topK : 10);
        return kbService.searchCompanyInterviews(req);
    }
}
