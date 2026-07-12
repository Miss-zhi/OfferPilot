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
public class SessionListItem {

    private String sessionId;
    private String title;
    private String activeFunction;
    private Integer messageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
