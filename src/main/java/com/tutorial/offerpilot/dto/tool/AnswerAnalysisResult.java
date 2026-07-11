/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 回答分析结果 — 工具返回原始数据 + 评估指导，由 LLM 据此生成评分和评语。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerAnalysisResult {

    /** 原始面试问题 */
    private String question;

    /** 用户回答原文 */
    private String answer;

    /** 评估指导文本，指示 LLM 如何分析回答 */
    private String guidance;

    /** 专业深度评分（0-100），由 LLM 在对话中生成，此处为 null */
    private Integer techScore;

    /** 表达能力评分（0-100），由 LLM 在对话中生成，此处为 null */
    private Integer exprScore;

    /** 内容覆盖度评分（0-100），由 LLM 在对话中生成，此处为 null */
    private Integer coverageScore;

    /** 亮点，由 LLM 生成，此处为空 */
    private List<String> highlights;

    /** 不足，由 LLM 生成，此处为空 */
    private List<String> weaknesses;

    /** 改进建议，由 LLM 生成，此处为空 */
    private String suggestion;

    /** 压力追问指导（仅在 PRESSURE 模式下非空），指示 LLM 如何追问 */
    private String followUpGuidance;
}
