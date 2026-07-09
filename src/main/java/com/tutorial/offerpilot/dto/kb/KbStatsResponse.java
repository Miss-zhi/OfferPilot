/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.kb;

import lombok.Data;

@Data
public class KbStatsResponse {

    private String kbId;
    private String name;
    private Integer documentCount;
    private Integer chunkCount;
    private Long activeDocuments;
    private Long failedDocuments;
}
