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
@Table(name = "op_scheduled_task_log",
        indexes = {
                @Index(name = "idx_task_log_name", columnList = "taskName"),
                @Index(name = "idx_task_log_start_time", columnList = "startTime")
        })
@Getter
@Setter
public class ScheduledTaskLog extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String taskName;

    @Column(length = 64)
    private String taskGroup = "DEFAULT";

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Instant startTime;

    private Instant endTime;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
