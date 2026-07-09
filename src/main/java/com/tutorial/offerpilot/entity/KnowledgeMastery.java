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
@Table(name = "op_knowledge_mastery",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_knowledge", columnNames = {"userId", "knowledgePoint"}),
        indexes = @Index(name = "idx_mastery_user_id", columnList = "userId"))
@Getter
@Setter
public class KnowledgeMastery extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 128)
    private String knowledgePoint;

    @Column(length = 64)
    private String category;

    @Column(nullable = false)
    private Integer score;

    private Integer previousScore;

    @Column(columnDefinition = "INT DEFAULT 1")
    private Integer assessCount = 1;

    private Instant lastAssessed;
}
