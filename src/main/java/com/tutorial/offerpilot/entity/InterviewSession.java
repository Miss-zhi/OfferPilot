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
@Table(name = "op_interview_session", indexes = @Index(name = "idx_session_user_id", columnList = "userId"))
@Getter
@Setter
public class InterviewSession extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 32)
    private String sessionType;

    @Column(length = 128)
    private String targetCompany;

    @Column(length = 32)
    private String interviewMode;

    private Integer overallScore;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer questionCount = 0;

    @Column(length = 32)
    private String status = "ACTIVE";

    private Instant startedAt;

    private Instant completedAt;
}
