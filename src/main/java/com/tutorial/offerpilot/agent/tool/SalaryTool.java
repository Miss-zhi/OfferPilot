/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.SalarySearchResult;
import com.tutorial.offerpilot.service.SalaryService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalaryTool {

    private final SalaryService salaryService;

    @Tool(name = "search_salary", description = "搜索指定公司和岗位的薪资数据，用于薪酬谈判参考")
    public SalarySearchResult searchSalary(
            @ToolParam(name = "company", description = "公司名称") String company,
            @ToolParam(name = "position", description = "岗位名称") String position) {
        log.info("search_salary called: company={}, position={}", company, position);
        return salaryService.searchSalary(company, position);
    }
}
