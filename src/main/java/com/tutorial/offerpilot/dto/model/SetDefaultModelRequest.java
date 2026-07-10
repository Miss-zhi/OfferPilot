/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetDefaultModelRequest {

    @NotNull(message = "modelConfigId 不能为空")
    private Long modelConfigId;

    @NotBlank(message = "modelName 不能为空")
    private String modelName;
}
