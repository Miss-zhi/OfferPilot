/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.kb;

import lombok.Data;

import java.util.List;

@Data
public class SearchTestResponse {

    private Integer total;
    private Long latencyMs;
    private List<SearchHit> hits;

    @Data
    public static class SearchHit {
        private String docId;
        private Integer chunkIndex;
        private String content;
        private Float score;
        private String tags;
        /** 召回来源：milvus / mysql，用于统计和输出映射 */
        private String source;
    }
}
