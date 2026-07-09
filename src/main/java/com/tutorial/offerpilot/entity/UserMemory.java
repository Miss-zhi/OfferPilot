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
@Table(name = "op_user_memory",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_memory", columnNames = {"userId", "memoryKey"}),
        indexes = {
                @Index(name = "idx_memory_user_id", columnList = "userId"),
                @Index(name = "idx_memory_category", columnList = "category")
        })
@Getter
@Setter
public class UserMemory extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 128)
    private String memoryKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String memoryContent;

    @Column(nullable = false, length = 32)
    private String category = "GENERAL";

    @Column(columnDefinition = "FLOAT DEFAULT 1.0")
    private Float relevanceScore = 1.0f;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer accessCount = 0;

    private Instant lastAccessed;
}
