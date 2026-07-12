/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageListItem {

    private Long id;
    private String sessionId;
    private String role;
    private String content;
    private String thinkingContent;
    private String toolCalls;
    private Integer seq;
    private Instant createdAt;
}
