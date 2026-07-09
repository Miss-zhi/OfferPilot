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
public class OfferComparisonResult {

    private String summary;
    private List<OfferAnalysis> analyses;
    private String recommendation;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfferAnalysis {
        private String company;
        private Double totalPackage;
        private List<String> pros;
        private List<String> cons;
    }
}
