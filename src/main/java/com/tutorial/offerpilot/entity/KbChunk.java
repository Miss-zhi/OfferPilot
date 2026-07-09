/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "kb_chunk",
        indexes = {
                @Index(name = "idx_chunk_doc_id", columnList = "docId"),
                @Index(name = "idx_chunk_kb_id", columnList = "kbId")
        })
@Getter
@Setter
public class KbChunk extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String docId;

    @Column(nullable = false, length = 64)
    private String kbId;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 64)
    private String contentHash;

    private Integer tokenCount;

    private Long milvusOffset;
}
