/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "kb_knowledge_base",
        indexes = {
                @Index(name = "idx_kb_owner", columnList = "ownerId"),
                @Index(name = "idx_kb_visibility", columnList = "visibility")
        })
@Getter
@Setter
public class KbKnowledgeBase extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String kbId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 128)
    private String milvusCollection;

    @Column(length = 64)
    private String category;

    @Column(length = 64)
    private String ownerId;

    @Column(nullable = false, length = 16)
    private String visibility = "PUBLIC";

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer documentCount = 0;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer chunkCount = 0;

    @Column(length = 32)
    private String status = "ACTIVE";
}
