/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorial.offerpilot.dto.salary.NegotiationScriptRequest;
import com.tutorial.offerpilot.dto.salary.OfferCompareRequest;
import com.tutorial.offerpilot.dto.tool.NegotiationScriptResult;
import com.tutorial.offerpilot.dto.tool.OfferComparisonResult;
import com.tutorial.offerpilot.dto.tool.SalarySearchResult;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.service.SalaryService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SalaryTool {

    private final SalaryService salaryService;
    private final ObjectMapper objectMapper;

    public SalaryTool(SalaryService salaryService, ObjectMapper objectMapper) {
        this.salaryService = salaryService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "search_salary", description = "搜索指定公司和岗位的薪资数据，用于薪酬谈判参考")
    public SalarySearchResult searchSalary(
            @ToolParam(name = "company", description = "公司名称") String company,
            @ToolParam(name = "position", description = "岗位名称") String position) {
        log.info("search_salary called: company={}, position={}", company, position);
        return salaryService.searchSalary(company, position);
    }

    /**
     * 生成薪资谈判话术建议（简单委托模式）。
     */
    @Tool(name = "generate_negotiation_script",
          description = "根据当前 offer 和目标期望薪资，生成薪资谈判话术建议。")
    public NegotiationScriptResult generateScript(
            @ToolParam(name = "current_offer",
                       description = "当前 offer 的文本描述，如'字节跳动 30k×15 + 期权'")
            String currentOffer,
            @ToolParam(name = "target_salary",
                       description = "目标期望年薪（万），如 50")
            double targetSalary,
            @ToolParam(name = "negotiation_style",
                       description = "谈判风格：assertive（强硬）/ moderate（温和）/ conservative（保守），默认 moderate")
            String negotiationStyle
    ) {
        log.info("generate_negotiation_script called: targetSalary={}, style={}",
                 targetSalary, negotiationStyle);
        NegotiationScriptRequest req = new NegotiationScriptRequest();
        req.setCurrentOffer(currentOffer);
        req.setTargetSalary(targetSalary);
        req.setNegotiationStyle(
                negotiationStyle != null ? negotiationStyle : "moderate");
        return salaryService.generateNegotiationScript(req);
    }

    /**
     * 对比多个 offer 的综合待遇（JSON 适配模式）。
     * LLM 传 JSON 字符串 → ObjectMapper 解析 → 调 Service。
     */
    @Tool(name = "compare_offers",
          description = "对比多个 offer 的综合待遇。输入多个 offer 详情 JSON 数组，生成对比分析报告和推荐建议。")
    public OfferComparisonResult compareOffers(
            @ToolParam(name = "offers_json", description = """
                    多个 offer 详情 JSON 数组。每个 offer 包含：
                    - company: 公司名称
                    - position: 岗位名称
                    - base: 月薪（K）
                    - months: 月数（如 15）
                    - bonus: 奖金信息（如 "3-6个月"）
                    - stock: 股票/期权信息
                    - location: 工作地点
                    示例: [{"company":"字节跳动","position":"Java后端","base":30,"months":15}]
                    """)
            String offersJson
    ) {
        log.info("compare_offers called");
        OfferCompareRequest req = parseOfferJson(offersJson);
        return salaryService.compareOffers(req);
    }

    /** 将 JSON 字符串解析为 OfferCompareRequest */
    private OfferCompareRequest parseOfferJson(String json) {
        try {
            return objectMapper.readValue(json, OfferCompareRequest.class);
        } catch (Exception e) {
            log.warn("Failed to parse offers JSON", e);
            throw new BusinessException(400,
                    "offer JSON 格式错误: " + e.getMessage());
        }
    }
}
