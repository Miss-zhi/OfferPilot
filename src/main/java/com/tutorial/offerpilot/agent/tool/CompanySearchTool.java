/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.CompanySearchResult;
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
            @ToolParam(name = "company_name", description = "公司名称") String companyName) {
        log.info("search_company_interviews called: company={}", companyName);
        return kbService.searchCompanyInterviews(companyName);
    }
}
