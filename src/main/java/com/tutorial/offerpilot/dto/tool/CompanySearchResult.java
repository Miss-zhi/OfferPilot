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
public class CompanySearchResult {

    private Integer total;
    private List<CompanyItem> companies;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyItem {
        private String companyName;
        private String interviewType;
        private String summary;
        private String difficulty;
        private Float relevanceScore;
        private String source;
    }
}
