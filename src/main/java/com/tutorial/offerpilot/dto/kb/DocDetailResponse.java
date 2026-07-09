/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.kb;

import lombok.EqualsAndHashCode;
import lombok.Data;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class DocDetailResponse extends DocResponse {

    private String errorMessage;
    private String metadataJson;
    private List<ChunkPreview> chunks;

    @Data
    public static class ChunkPreview {
        private Integer chunkIndex;
        private String content;
        private Integer tokenCount;
    }
}
