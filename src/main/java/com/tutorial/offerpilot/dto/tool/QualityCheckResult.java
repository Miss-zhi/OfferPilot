/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简历质量检查结果 — 工具提供原始文本片段和基础统计，LLM 据此进行三项专项检查。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityCheckResult {

    /** LLM 评估指导文本（含三项检查指令） */
    private String guidance;

    /** 原始文本片段（技能段落、经历段落） */
    private List<RawData> rawData;

    /** 问题总数（由 LLM 填充，工具返回 0） */
    private int totalIssues;

    /**
     * 原始文本数据片段。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawData {

        /** 段落标识（如 "技能段落"、"项目经历段落"） */
        private String section;

        /** 段落原文 */
        private String content;

        /** 段落统计信息 */
        private String stats;
    }
}
