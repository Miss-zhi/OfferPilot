/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * JD 匹配结果 — 工具计算匹配度基础数据，LLM 据此生成自然语言评估。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JdMatchResult {

    /** LLM 评估指导文本（含 matched/missing 清单） */
    private String guidance;

    /** 匹配度评分 0-100，基于关键词覆盖率 */
    private int score;

    /** 匹配率百分比 */
    private double matchRate;

    /** 简历已匹配的 JD 技能关键词 */
    private List<String> matched;

    /** JD 中有但简历中缺失的技能关键词 */
    private List<String> missing;
}
