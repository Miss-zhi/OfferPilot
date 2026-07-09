/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.kb;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKbRequest {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;
    private String category;
}
