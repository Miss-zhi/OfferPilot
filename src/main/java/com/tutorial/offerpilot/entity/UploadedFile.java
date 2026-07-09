/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "op_file", indexes = @Index(name = "idx_file_user_id", columnList = "userId"))
@Getter
@Setter
public class UploadedFile extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String fileId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 256)
    private String fileName;

    @Column(nullable = false, length = 512)
    private String filePath;

    @Column(nullable = false, length = 32)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(length = 32)
    private String status = "UPLOADED";
}
