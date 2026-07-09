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
public class ResumeEvaluateResult {

    private Integer overallScore;
    private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;
}
