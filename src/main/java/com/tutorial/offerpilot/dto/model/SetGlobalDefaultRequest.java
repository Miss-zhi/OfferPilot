/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetGlobalDefaultRequest {

    @NotBlank(message = "modelName 不能为空")
    private String modelName;
}
