/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.kb;

import lombok.Data;

@Data
public class KbResponse {

    private String kbId;
    private String name;
    private String description;
    private String category;
    private String visibility;
    private String status;
    private Integer documentCount;
    private Integer chunkCount;
}
