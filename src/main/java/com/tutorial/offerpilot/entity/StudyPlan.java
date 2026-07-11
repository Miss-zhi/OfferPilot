/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "op_study_plan", indexes = @Index(name = "idx_plan_user_id", columnList = "userId"))
@Getter
@Setter
public class StudyPlan extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private LocalDate weekEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tasksJson;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer completedCount = 0;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer totalCount = 0;

    @Column(length = 32)
    private String status = "ACTIVE";

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer priorityOrder = 0;

    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private Boolean reminderEnabled = true;
}
