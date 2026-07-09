/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "kb_search_log",
        indexes = {
                @Index(name = "idx_search_log_kb_id", columnList = "kbId"),
                @Index(name = "idx_search_log_created_at", columnList = "createdAt")
        })
@Getter
@Setter
public class KbSearchLog extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String kbId;

    @Column(nullable = false, length = 2000)
    private String queryText;

    @Column(length = 500)
    private String filterExpr;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer resultCount = 0;

    private Float topScore;

    private Float avgScore;

    private Integer latencyMs;

    @Column(length = 64)
    private String userId;
}
