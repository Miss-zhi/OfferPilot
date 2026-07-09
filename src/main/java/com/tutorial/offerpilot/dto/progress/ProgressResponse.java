/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.progress;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProgressResponse {

    private String period;
    private Long interviewCount;
    private List<Integer> scoreTrend;
    private Map<String, MasteryInfo> knowledgeMastery;
    private StudyPlanInfo studyPlan;

    @Data
    public static class MasteryInfo {
        private Integer first;
        private Integer current;
        private String trend;
    }

    @Data
    public static class StudyPlanInfo {
        private Integer completed;
        private Integer total;
    }
}
