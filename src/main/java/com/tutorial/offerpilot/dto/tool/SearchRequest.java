/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
     * 使用 Milvus 标量过滤语法，字符串值需转义双引号。
     *
     * @return Milvus filter 表达式，无条件时返回 null（不做过滤）
     */
    public String buildFilterExpr() {
        List<String> conditions = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            conditions.add("category == \"" + escapeMilvus(category) + "\"");
        }
        if (difficulty != null && !difficulty.isBlank()) {
            conditions.add("difficulty == \"" + escapeMilvus(difficulty) + "\"");
        }
        if (position != null && !position.isBlank()) {
            conditions.add("position == \"" + escapeMilvus(position) + "\"");
        }

        if (conditions.isEmpty()) {
            return null;
        }
        return String.join(" && ", conditions);
    }

    /**
     * 转义 Milvus 标量过滤表达式中的特殊字符（双引号、反斜杠）。
     */
    private static String escapeMilvus(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
