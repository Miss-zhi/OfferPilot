/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 面试模式策略服务 — 提供模式感知的阶段轮转、难度递进和简历关键词提取。
 *
 * <p>不包含任何硬编码的题目模板或自然语言内容，所有策略仅返回结构化数据，
 * 自然语言出题指导由 {@link com.tutorial.offerpilot.agent.tool.MockInterviewTool} 构建。
 */
@Service
public class InterviewModeService {

    /** 默认回退阶段序列（12 个阶段） */
    private static final List<String> DEFAULT_PHASE_SEQUENCE = List.of(
            "自我介绍", "专业技能", "专业技能", "项目经验",
            "专业技能", "情景分析", "行为面试", "项目经验",
            "专业技能", "情景分析", "行为面试", "职业规划"
    );

    /** TECH_DEEP 模式：深度挖掘技术栈和项目经验 */
    private static final List<String> TECH_DEEP_PHASES = List.of(
            "自我介绍", "基础知识", "项目经验", "项目经验",
            "项目经验", "系统设计", "项目经验", "底层原理",
            "架构设计", "性能优化", "项目经验", "职业规划"
    );

    /** BEHAVIOR 模式：侧重软素质和沟通能力 */
    private static final List<String> BEHAVIOR_PHASES = List.of(
            "自我介绍", "项目经验", "行为面试", "行为面试",
            "项目经验", "情景分析", "行为面试", "压力测试",
            "项目经验", "行为面试", "情景分析", "职业规划"
    );

    /** SYSTEM_DESIGN 模式：考察架构设计能力 */
    private static final List<String> SYSTEM_DESIGN_PHASES = List.of(
            "自我介绍", "基础知识", "系统设计", "系统设计",
            "基础知识", "系统设计", "架构设计", "系统设计",
            "性能优化", "系统设计", "架构设计", "职业规划"
    );

    /** PRESSURE 模式：测试抗压能力和临场反应 */
    private static final List<String> PRESSURE_PHASES = List.of(
            "自我介绍", "专业技能", "专业技能", "项目经验",
            "压力测试", "情景分析", "压力测试", "项目经验",
            "压力测试", "专业知识", "压力测试", "行为面试"
    );

    /**
     * 获取各模式下的阶段序列（12 阶段轮转）。
     */
    public List<String> getPhaseSequence(String mode) {
        if (mode == null) {
            return DEFAULT_PHASE_SEQUENCE;
        }
        return switch (mode) {
            case "TECH_DEEP" -> TECH_DEEP_PHASES;
            case "BEHAVIOR" -> BEHAVIOR_PHASES;
            case "SYSTEM_DESIGN" -> SYSTEM_DESIGN_PHASES;
            case "PRESSURE" -> PRESSURE_PHASES;
            default -> DEFAULT_PHASE_SEQUENCE;
        };
    }

    /**
     * 模式感知的难度递进策略。
     *
     * @param mode           面试模式（TECH_DEEP / BEHAVIOR / SYSTEM_DESIGN / PRESSURE）
     * @param avgScore       历史均分（null 表示无历史数据）
     * @param questionNumber 当前第几题（1-based）
     * @return 难度标签：easy / medium / hard
     */
    public String determineDifficulty(String mode, Double avgScore, int questionNumber) {
        if (mode == null) {
            return defaultDifficulty(avgScore, questionNumber);
        }
        return switch (mode) {
            case "PRESSURE" -> pressureDifficulty(avgScore, questionNumber);
            case "SYSTEM_DESIGN" -> systemDesignDifficulty(questionNumber);
            default -> defaultDifficulty(avgScore, questionNumber);
        };
    }

    /** 压力模式：起步 medium，均分 >= 60 或 3 题后直接 hard */
    private String pressureDifficulty(Double avgScore, int questionNumber) {
        if (avgScore != null && !avgScore.isNaN() && avgScore >= 60) {
            return "hard";
        }
        if (questionNumber > 3) {
            return "hard";
        }
        return "medium";
    }

    /** 系统设计模式：渐进式递进 */
    private String systemDesignDifficulty(int questionNumber) {
        if (questionNumber <= 2) {
            return "easy";
        }
        if (questionNumber <= 5) {
            return "medium";
        }
        return "hard";
    }

    /** 默认难度递进：基于均分或题号 */
    private String defaultDifficulty(Double avgScore, int questionNumber) {
        if (avgScore != null && !avgScore.isNaN()) {
            if (avgScore >= 75) {
                return "hard";
            }
            if (avgScore >= 50) {
                return "medium";
            }
            return "easy";
        }
        if (questionNumber > 5) {
            return "hard";
        }
        if (questionNumber > 2) {
            return "medium";
        }
        return "easy";
    }

    /**
     * 从简历文本提取项目相关片段（不做技术关键词识别，交给 LLM）。
     *
     * @param resumeText 简历文本
     * @return 项目相关行（去重，最多 10 行）
     */
    public List<String> extractProjectKeywords(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return List.of();
        }
        List<String> projectFragments = new ArrayList<>();
        for (String line : resumeText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("项目") || trimmed.contains("实习") || trimmed.contains("开源")
                    || trimmed.contains("负责") || trimmed.contains("参与")) {
                projectFragments.add(trimmed);
            }
        }
        return projectFragments.stream().distinct().limit(10).toList();
    }
}
