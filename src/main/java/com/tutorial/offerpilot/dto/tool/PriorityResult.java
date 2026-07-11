/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 优先级排序结果 — 工具返回量化排序后的薄弱点列表 + LLM 指导。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriorityResult {

    /** LLM 指导文本，包含排序规则说明和优先级表格 */
    private String guidance;

    /** 按优先级降序排列的薄弱知识点列表 */
    private List<RankedItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedItem {

        /** 知识点名称 */
        private String topic;

        /** 当前掌握度（0-100） */
        private Integer currentScore;

        /** 该知识点在搜索日志/题库中的出现频次 */
        private Integer frequency;

        /** 优先级分数 = frequency × (100 - currentScore) */
        private Integer priority;

        /** 紧急度：HIGH / MEDIUM / LOW */
        private String urgency;
    }
}
