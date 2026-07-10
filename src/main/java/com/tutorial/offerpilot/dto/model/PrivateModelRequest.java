/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PrivateModelRequest {

    @NotBlank(message = "provider 不能为空")
    private String provider;

    @NotBlank(message = "apiKey 不能为空")
    private String apiKey;

    /** 模型列表链接（可选） */
    private String modelListUrl;

    @NotBlank(message = "modelName 不能为空")
    private String modelName;
}
