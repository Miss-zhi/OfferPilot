/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 学习进度结果 — 工具返回结构化数据 + 汇总指导，由 LLM 据此生成自然语言总结。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResult {

    private Long interviewCount;
    private Integer averageScore;
    private Map<String, Integer> knowledgeScores;

    /** 汇总指导文本，指示 LLM 如何组织进度总结 */
    private String guidance;

    /** 学习计划完成度 */
    private Integer completedTasks;
    private Integer totalTasks;
}
