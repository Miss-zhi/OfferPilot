/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简历评估结果 — 工具返回原始数据 + 评估指导，由 LLM 据此生成评分和建议。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeEvaluateResult {

    /** 简历原文 */
    private String resumeText;

    /** 评估指导文本，指示 LLM 如何评估简历 */
    private String guidance;

    /** 综合评分（0-100），由 LLM 生成，此处为 null */
    private Integer overallScore;

    /** 总结，由 LLM 生成，此处为空 */
    private String summary;

    /** 优点，由 LLM 生成，此处为空 */
    private List<String> strengths;

    /** 不足，由 LLM 生成，此处为空 */
    private List<String> weaknesses;

    /** 改进建议，由 LLM 生成，此处为空 */
    private List<String> suggestions;
}
