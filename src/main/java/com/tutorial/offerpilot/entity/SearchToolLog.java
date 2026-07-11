/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 搜索工具链日志。
 * 记录每次搜索调用的详细指标，包括各来源命中数和耗时。
 */
@Entity
@Table(name = "op_search_tool_log",
        indexes = {
                @Index(name = "idx_stl_user_id", columnList = "userId"),
                @Index(name = "idx_stl_query_text", columnList = "queryText"),
                @Index(name = "idx_stl_zero_result", columnList = "zeroResult"),
                @Index(name = "idx_stl_created_at", columnList = "createdAt")
        })
@Getter
@Setter
public class SearchToolLog extends BaseEntity {

    @Column(length = 64)
    private String userId;

    @Column(nullable = false, length = 500)
    private String queryText;

    @Column(columnDefinition = "TEXT")
    private String expandedQueries;

    @Column(nullable = false, length = 64)
    private String toolName;

    @Column(length = 32)
    private String intent;

    @Column(nullable = false)
    private Integer milvusHits = 0;

    @Column(nullable = false)
    private Integer dbHits = 0;

    @Column(nullable = false)
    private Integer webHits = 0;

    @Column(nullable = false)
    private Integer totalResults = 0;

    @Column(nullable = false)
    private Integer zeroResult = 0;

    @Column(nullable = false)
    private Long latencyMs = 0L;
}
