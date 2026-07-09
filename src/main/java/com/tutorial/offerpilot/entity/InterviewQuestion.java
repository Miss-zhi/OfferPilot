/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "op_interview_question", indexes = @Index(name = "idx_question_session_id", columnList = "sessionId"))
@Getter
@Setter
public class InterviewQuestion extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(length = 32)
    private String questionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    private Integer techScore;

    private Integer exprScore;

    private Integer coverageScore;

    @Column(columnDefinition = "TEXT")
    private String highlights;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer sortOrder = 0;
}
