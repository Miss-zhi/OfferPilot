/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageSaveRequest {

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private String thinkingContent;

    private String toolCalls;

    /** 自动创建 session 时使用的功能标识 */
    private String activeFunction;
}
