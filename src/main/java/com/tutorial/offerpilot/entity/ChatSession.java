/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "op_chat_session", indexes = {
        @Index(name = "idx_session_user_id", columnList = "userId, updatedAt")
})
@Getter
@Setter
public class ChatSession extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(length = 200)
    private String title = "";

    @Column(length = 64)
    private String activeFunction = "";

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer messageCount = 0;
}
