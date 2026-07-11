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
public class ResourceListResult {

    private Integer total;
    private List<ResourceItem> resources;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceItem {
        private String title;
        private String url;
        private String type;
        private Float relevanceScore;
        private String source;
    }
}
