/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConfirmRequest {

    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    @NotEmpty(message = "确认列表不能为空")
    @Valid
    private List<ConfirmItem> confirmations;

    @Data
    public static class ConfirmItem {

        /** 对应 ToolUseBlock.id */
        @NotBlank(message = "toolCallId 不能为空")
        private String toolCallId;

        /** 对应 ToolUseBlock.name */
        @NotBlank(message = "toolCallName 不能为空")
        private String toolCallName;

        /** 对应 ToolUseBlock.input（保留原始参数） */
        private Map<String, Object> toolCallInput;

        /** 是否批准该工具调用 */
        private boolean confirmed;
    }
}
