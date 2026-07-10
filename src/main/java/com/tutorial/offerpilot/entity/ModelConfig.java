/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.entity;

import com.tutorial.offerpilot.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型配置表 — 存储 LLM Provider 的接入配置。
 */
@Entity
@Table(name = "op_model_config",
        indexes = {
                @Index(name = "idx_is_enabled", columnList = "isEnabled"),
                @Index(name = "idx_is_global_default", columnList = "isGlobalDefault"),
                @Index(name = "idx_provider", columnList = "provider")
        })
@Getter
@Setter
public class ModelConfig extends BaseEntity {

    /** 模型提供方标识，如 dashscope / openai / anthropic */
    @Column(nullable = false, length = 64)
    private String provider;

    /** API Base URL */
    @Column(name = "base_url", nullable = false, length = 256)
    private String baseUrl;

    /** API Key（AES 加密存储） */
    @Column(name = "api_key", nullable = false, length = 512)
    private String apiKey;

    /** API 格式: openai / anthropic / gemini */
    @Column(name = "api_format", nullable = false, length = 16)
    private String apiFormat;

    /** 认证 Header 类型: bearer / x-api-key / x-goog-api-key / none */
    @Column(name = "auth_header_type", nullable = false, length = 16)
    private String authHeaderType;

    /** 模型列表获取链接 */
    @Column(name = "model_list_url", nullable = false, length = 512)
    private String modelListUrl;

    /** 该配置下的默认模型名称 */
    @Column(name = "default_model_name", length = 128)
    private String defaultModelName;

    /** 是否启用 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    /** 是否为全局默认模型配置 */
    @Column(name = "is_global_default", nullable = false)
    private Boolean isGlobalDefault = false;

    /** 是否为用户私有模型 */
    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = false;
}
