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
@Table(name = "kb_document",
        indexes = {
                @Index(name = "idx_doc_kb_id", columnList = "kbId"),
                @Index(name = "idx_doc_status", columnList = "status")
        })
@Getter
@Setter
public class KbDocument extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String docId;

    @Column(nullable = false, length = 64)
    private String kbId;

    @Column(nullable = false, length = 256)
    private String fileName;

    @Column(nullable = false, length = 512)
    private String filePath;

    @Column(nullable = false, length = 32)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer chunkCount = 0;

    @Column(length = 64)
    private String chunkStrategy = "AUTO";

    @Column(length = 32)
    private String status = "UPLOADED";

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer progress = 0;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(length = 512)
    private String tags;

    private Instant uploadedAt;

    private Instant indexedAt;
}
