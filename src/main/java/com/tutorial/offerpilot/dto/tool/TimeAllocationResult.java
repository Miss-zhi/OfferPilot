/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 时间分配分析结果 — 各题时长估算 + 评估枚举 + LLM 指导。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeAllocationResult {

    /** 评估指导文本，指示 LLM 如何生成时间分配维度的自然语言分析 */
    private String guidance;

    /** 各题时间分配明细 */
    private List<TimeItem> items;

    /** 总估算时长（秒） */
    private int totalSeconds;

    /** 过短的题目数 */
    private int tooShortCount;

    /** 过长的题目数 */
    private int tooLongCount;

    /**
     * 单题时间分配项。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeItem {
        /** 题目文本（截断至 50 字） */
        private String question;
        /** 回答字符数（去除空白） */
        private int charCount;
        /** 估算时长（秒） */
        private int estimatedSeconds;
        /** 评估枚举：GOOD / ACCEPTABLE / TOO_SHORT / TOO_LONG */
        private String assessment;
    }
}
