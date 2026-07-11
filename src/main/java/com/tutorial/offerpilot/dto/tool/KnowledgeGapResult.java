/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识盲区检测结果 — 词法对比数据 + LLM 评估指导。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGapResult {

    /** 评估指导文本，指示 LLM 如何基于词法对比进行语义级别复核 */
    private String guidance;

    /** 词法匹配到的知识点列表 */
    private List<String> covered;

    /** 词法遗漏的知识点列表（LLM 需语义复核是否为真正的盲区） */
    private List<String> missing;

    /** 词法覆盖率（0-100），辅助指标，LLM 应做语义级别判断 */
    private int coverageRate;
}
