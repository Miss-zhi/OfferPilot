# Copyright (c) 2020-06-29 Qoder. All rights reserved.

-- ================================================================
-- LLM 模型配置前置管理 - 数据库变更
-- ================================================================

-- 1. 新建 op_model_config 表
CREATE TABLE op_model_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    provider            VARCHAR(64)   NOT NULL COMMENT '模型提供方，如 dashscope / openai / anthropic',
    base_url            VARCHAR(256)  NOT NULL COMMENT 'API Base URL',
    api_key             VARCHAR(512)  NOT NULL COMMENT 'API Key（AES加密存储）',
    api_format          VARCHAR(16)   NOT NULL DEFAULT 'openai' COMMENT 'API格式: openai / anthropic / gemini',
    auth_header_type    VARCHAR(16)   NOT NULL DEFAULT 'bearer' COMMENT '认证Header类型: bearer / x-api-key / x-goog-api-key / none',
    model_list_url      VARCHAR(512)  NOT NULL COMMENT '模型列表获取链接',
    default_model_name  VARCHAR(128)  DEFAULT NULL COMMENT '该配置下的默认模型名称',
    is_enabled          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_global_default   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否为全局默认模型配置',
    is_private          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否为用户私有模型',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    update_by           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    INDEX idx_is_enabled (is_enabled),
    INDEX idx_is_global_default (is_global_default),
    INDEX idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- 2. 新建 op_model_name 表
CREATE TABLE op_model_name (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    model_config_id BIGINT        NOT NULL COMMENT '关联 op_model_config.id',
    model_name      VARCHAR(128)  NOT NULL COMMENT '模型名称，如 qwen-max / gpt-4',
    is_available    TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '管理员是否勾选为可用',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_model_config_id (model_config_id),
    CONSTRAINT fk_model_name_config FOREIGN KEY (model_config_id) REFERENCES op_model_config(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型名称表（从API拉取）';

-- 3. 修改 op_user 表（新增模型配置外键）
ALTER TABLE op_user
    ADD COLUMN default_model_config_id BIGINT DEFAULT NULL COMMENT '用户默认模型配置ID，关联 op_model_config.id',
    ADD COLUMN private_model_config_id BIGINT DEFAULT NULL COMMENT '用户私有模型配置ID（若非空则优先使用）',
    ADD CONSTRAINT fk_user_model_config FOREIGN KEY (default_model_config_id) REFERENCES op_model_config(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_user_private_model FOREIGN KEY (private_model_config_id) REFERENCES op_model_config(id) ON DELETE SET NULL;
