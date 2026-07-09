/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.kb;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchTestRequest {

    @NotBlank(message = "查询文本不能为空")
    private String query;

    private String filterExpr;
    private Integer topK = 5;
}
