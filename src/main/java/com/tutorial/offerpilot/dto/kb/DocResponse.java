/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.kb;

import lombok.Data;

import java.time.Instant;

@Data
public class DocResponse {

    private String docId;
    private String kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private String chunkStrategy;
    private String status;
    private Integer progress;
    private String tags;
    private Instant uploadedAt;
    private Instant indexedAt;
}
