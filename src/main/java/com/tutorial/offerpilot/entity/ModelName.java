/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型名称表 — 从 LLM Provider API 拉取的模型名称列表。
 */
@Entity
@Table(name = "op_model_name",
        indexes = {
                @Index(name = "idx_model_config_id", columnList = "modelConfigId")
        })
@Getter
@Setter
public class ModelName extends BaseEntity {

    /** 关联的模型配置 ID */
    @Column(name = "model_config_id", nullable = false)
    private Long modelConfigId;

    /** 模型名称，如 qwen-max / gpt-4 */
    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    /** 管理员是否勾选为可用 */
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;
}
