/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import com.tutorial.offerpilot.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "op_user")
@Getter
@Setter
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String userId;

    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(length = 256)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 128)
    private String targetCompany;

    @Column(length = 128)
    private String targetPosition;

    private Instant lastLoginAt;

    /** 用户默认模型配置ID，关联 op_model_config.id */
    @Column(name = "default_model_config_id")
    private Long defaultModelConfigId;

    /** 用户私有模型配置ID（若非空则优先使用） */
    @Column(name = "private_model_config_id")
    private Long privateModelConfigId;
}
