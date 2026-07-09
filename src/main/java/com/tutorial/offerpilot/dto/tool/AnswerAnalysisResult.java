/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerAnalysisResult {

    private Integer techScore;
    private Integer exprScore;
    private Integer coverageScore;
    private List<String> highlights;
    private List<String> weaknesses;
    private String suggestion;
}
