/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 搜索反馈记录。
 * 追踪用户是否采纳搜索结果，用于评估搜索质量。
 */
@Entity
@Table(name = "op_search_feedback",
        indexes = {
                @Index(name = "idx_sf_user_id", columnList = "userId"),
                @Index(name = "idx_sf_query_text", columnList = "queryText"),
                @Index(name = "idx_sf_created_at", columnList = "createdAt")
        })
@Getter
@Setter
public class SearchFeedback extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 500)
    private String queryText;

    @Column(nullable = false, length = 64)
    private String toolName;

    @Column(nullable = false, length = 32)
    private String resultSource;

    @Column(nullable = false)
    private Integer resultCount = 0;

    /** 是否有用: 1=有用 0=无用 null=未知 */
    private Boolean helpful;

    @Column(length = 64)
    private String sessionId;
}
