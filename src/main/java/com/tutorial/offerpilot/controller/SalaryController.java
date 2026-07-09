/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.salary.NegotiationScriptRequest;
import com.tutorial.offerpilot.dto.salary.OfferCompareRequest;
import com.tutorial.offerpilot.dto.salary.SalarySearchRequest;
import com.tutorial.offerpilot.dto.tool.NegotiationScriptResult;
import com.tutorial.offerpilot.dto.tool.OfferComparisonResult;
import com.tutorial.offerpilot.dto.tool.SalarySearchResult;
import com.tutorial.offerpilot.service.SalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/offerpilot/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;

    @GetMapping("/search")
    public ApiResponse<SalarySearchResult> search(@Valid SalarySearchRequest request) {
        log.info("Salary search: company={}, position={}", request.getCompany(), request.getPosition());
        SalarySearchResult result = salaryService.searchSalary(
                request.getCompany(), request.getPosition());
        return ApiResponse.success(result);
    }

    @PostMapping("/compare")
    public ApiResponse<OfferComparisonResult> compare(@RequestBody @Valid OfferCompareRequest request) {
        log.info("Offer compare: {} offers", request.getOffers().size());
        OfferComparisonResult result = salaryService.compareOffers(request);
        return ApiResponse.success(result);
    }

    @PostMapping("/negotiation-script")
    public ApiResponse<NegotiationScriptResult> negotiationScript(
            @RequestBody @Valid NegotiationScriptRequest request) {
        log.info("Negotiation script: targetSalary={}", request.getTargetSalary());
        NegotiationScriptResult result = salaryService.generateNegotiationScript(request);
        return ApiResponse.success(result);
    }
}
