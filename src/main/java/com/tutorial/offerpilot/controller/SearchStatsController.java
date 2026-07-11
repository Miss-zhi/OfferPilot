/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.service.SearchAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 搜索统计接口。
 * 提供搜索分析数据，包括热门查询、零结果分析和来源分布。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/kb/search")
@RequiredArgsConstructor
public class SearchStatsController {

    private final SearchAnalyticsService analyticsService;

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        log.info("Search stats requested");
        return ApiResponse.success(analyticsService.getStats());
    }
}
