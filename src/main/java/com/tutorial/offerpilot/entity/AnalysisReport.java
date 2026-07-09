/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "op_analysis_report", indexes = @Index(name = "idx_report_user_id", columnList = "userId"))
@Getter
@Setter
public class AnalysisReport extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String reportId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(length = 64)
    private String sessionId;

    @Column(nullable = false, length = 32)
    private String reportType;

    private Integer overallScore;

    @Column(columnDefinition = "TEXT")
    private String dimensionsJson;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    @Column(columnDefinition = "TEXT")
    private String improvementsJson;
}
