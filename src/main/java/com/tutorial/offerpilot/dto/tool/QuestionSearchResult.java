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
public class QuestionSearchResult {

    private Integer total;
    private List<QuestionItem> questions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionItem {
        private String questionId;
        private String content;
        private String category;
        private Float relevanceScore;
    }
}
