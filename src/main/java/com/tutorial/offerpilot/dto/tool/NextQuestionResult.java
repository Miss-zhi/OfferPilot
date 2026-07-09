/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 出题指导结果 — 工具返回结构化上下文，由 LLM 据此生成实际面试题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NextQuestionResult {

    /** LLM 出题指令，如 "请为产品经理岗位生成一道专业技能类面试题，难度medium，这是第3题。建议关注需求分析方法论。请直接输出题目文本。" */
    private String guidance;

    /** 面试阶段：自我介绍/专业技能/项目经验/情景分析/行为面试/职业规划 */
    private String category;

    /** 难度：easy/medium/hard */
    private String difficulty;

    /** 面试岗位/方向，如 "产品经理" */
    private String interviewRole;

    /** 当前是第几题（1-based） */
    private Integer questionNumber;

    /** 历史均分（0-100），无历史时为 null */
    private Double averageScore;

    /** 前几题的文本摘要，用于 LLM 避免出重复题 */
    private List<String> previousQuestions;
}
