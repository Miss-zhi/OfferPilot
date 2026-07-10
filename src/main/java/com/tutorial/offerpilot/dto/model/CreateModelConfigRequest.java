/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateModelConfigRequest {

    /** 系统预设 provider key */
    @NotBlank(message = "provider 不能为空")
    private String provider;

    /** API Key */
    @NotBlank(message = "apiKey 不能为空")
    private String apiKey;

    /** 模型列表链接（可选，不传则使用预设默认值） */
    private String modelListUrl;
}
