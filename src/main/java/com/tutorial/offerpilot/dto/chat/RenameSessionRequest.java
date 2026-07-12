/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RenameSessionRequest {

    @NotBlank(message = "标题不能为空")
    private String title;
}
