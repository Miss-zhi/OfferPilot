/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 自信度分析结果 — 口头禅统计 + 自我修正检测 + 自信度评分 + LLM 指导。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfidenceResult {

    /** 评估指导文本，指示 LLM 如何生成自信度维度的自然语言评语 */
    private String guidance;

    /** 自信度评分（0-100），越高越自信 */
    private int confidenceScore;

    /** 口头禅统计：词 → 出现次数 */
    private Map<String, Integer> fillerCounts;

    /** 口头禅密度（次/千字） */
    private double fillerDensity;

    /** 自我修正次数 */
    private int correctionCount;
}
