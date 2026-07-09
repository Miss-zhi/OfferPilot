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
public class SalarySearchResult {

    private Integer total;
    private List<SalaryItem> salaries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalaryItem {
        private String company;
        private String position;
        private String baseRange;
        private String bonusRange;
        private String stockInfo;
        private String source;
        private Float relevanceScore;
    }
}
