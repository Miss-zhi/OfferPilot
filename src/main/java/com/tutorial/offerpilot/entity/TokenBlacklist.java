/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "op_token_blacklist",
        indexes = {
                @Index(name = "idx_blacklist_user_id", columnList = "userId"),
                @Index(name = "idx_blacklist_expire_at", columnList = "expireAt")
        })
@Getter
@Setter
public class TokenBlacklist extends BaseEntity {

    @Column(nullable = false, unique = true, length = 128)
    private String tokenJti;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private Instant expireAt;

    private Instant blacklistedAt;

    @Column(length = 64)
    private String reason = "LOGOUT";
}
