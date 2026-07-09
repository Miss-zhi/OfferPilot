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
public class AnswerSearchResult {

    private Integer total;
    private List<AnswerItem> answers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerItem {
        private String answerId;
        private String question;
        private String answer;
        private String category;
        private Float relevanceScore;
    }
}
