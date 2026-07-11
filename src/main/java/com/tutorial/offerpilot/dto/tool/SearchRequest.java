/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.Data;

/**
 * 结构化搜索请求参数。
 * 替代单 keyword 字符串，支持分类/难度/公司/岗位等多维度过滤和个性化搜索。
 */
@Data
public class SearchRequest {

    /** 搜索关键词，多个用空格分隔 */
    private String keywords;

    /** 分类过滤：专业技能/项目经验/情景分析/行为面试/职业规划 */
    private String category;

    /** 难度过滤：easy/medium/hard */
    private String difficulty;

    /** 公司名称过滤 */
    private String company;

    /** 岗位名称过滤 */
    private String position;

    /** 返回数量，默认 10，最大 50 */
    private Integer topK = 10;

    /** 用户ID（用于个性化排序和日志记录） */
    private String userId;

    /**
     * 根据结构化参数构建 Milvus filter 表达式。
     * 格式：category == "专业技能" && difficulty == "hard"
     */
    public String buildFilterExpr() {
        StringBuilder sb = new StringBuilder();
        boolean hasFilter = false;

        if (category != null && !category.isBlank()) {
            sb.append("category == \"").append(escapeFilterValue(category)).append("\"");
            hasFilter = true;
        }

        if (difficulty != null && !difficulty.isBlank()) {
            if (hasFilter) sb.append(" && ");
            sb.append("difficulty == \"").append(escapeFilterValue(difficulty)).append("\"");
            hasFilter = true;
        }

        return hasFilter ? sb.toString() : null;
    }

    private String escapeFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
