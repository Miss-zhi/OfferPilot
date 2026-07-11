/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * STAR 检查结果 — 工具分段分析每段经历，LLM 据此判断 S/T/A/R 完整性。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StarCheckResult {

    /** LLM 评估指导文本（含 S/T/A/R 检查指令） */
    private String guidance;

    /** 各段经历的检测项 */
    private List<StarItem> items;

    /** 经历总数 */
    private int totalCount;

    /**
     * 单段经历的 STAR 检测项。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StarItem {

        /** 经历序号（1-based） */
        private int index;

        /** 经历原文前 200 字 */
        private String content;
    }
}
