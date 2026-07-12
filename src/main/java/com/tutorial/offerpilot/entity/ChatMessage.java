/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "op_chat_message", indexes = {
        @Index(name = "idx_msg_session_seq", columnList = "sessionId, seq")
})
@Getter
@Setter
public class ChatMessage extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 8)
    private String role;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String thinkingContent;

    @Column(columnDefinition = "JSON")
    private String toolCalls;

    @Column(nullable = false)
    private Integer seq = 0;
}
