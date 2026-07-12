/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.Data;

import java.util.List;

@Data
public class KbListResult {

    private int total;
    private List<KbInfo> items;

    @Data
    public static class KbInfo {
        private String kbId;
        private String name;
        private String description;
        private String visibility;
        private Integer documentCount;
        private Integer chunkCount;
    }
}
