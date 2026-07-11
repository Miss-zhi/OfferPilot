/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一智能搜索返回结果。
 * 替代 4 个独立工具的返回类型，提供跨类型的统一搜索结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmartSearchResult {

    /** 查询意图分类：practice/learn/company/salary/general */
    private String intent;

    /** Query 扩展后的检索短语列表 */
    private List<String> expandedQueries;

    /** 返回结果总数 */
    private Integer total;

    /** 搜索耗时（毫秒） */
    private Long latencyMs;

    /** 结果来源分布 */
    private String primarySource;

    /** 统合搜索结果列表 */
    private List<SmartSearchItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmartSearchItem {
        /** 结果类型：question/answer/company_interview/resource */
        private String type;

        /** 标题/摘要 */
        private String title;

        /** 完整内容/片段 */
        private String snippet;

        /** 分类 */
        private String category;

        /** 难度 */
        private String difficulty;

        /** 相关性分数 */
        private Float relevanceScore;

        /** 来源：kb/db/web */
        private String source;

        /** 关联公司（公司调研类型） */
        private String companyName;

        /** 关联URL（学习资源类型） */
        private String url;
    }
}
