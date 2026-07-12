-- Copyright (c) 2020-06-29 Qoder. All rights reserved.
-- 对话历史持久化 — 会话表 + 消息表

-- ============================================================
-- 1. 聊天会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS `op_chat_session`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`      VARCHAR(64)  NOT NULL COMMENT '会话唯一标识',
    `user_id`         VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `title`           VARCHAR(200) NOT NULL DEFAULT '' COMMENT '会话标题（自动取首条消息截断）',
    `active_function` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '当前功能模式（resume/mock_interview/interview_analysis）',
    `message_count`   INT          NOT NULL DEFAULT 0 COMMENT '消息数量',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `update_by`       VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id_updated` (`user_id`, `updated_at` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '聊天会话表';

-- ============================================================
-- 2. 聊天消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS `op_chat_message`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`       VARCHAR(64)  NOT NULL COMMENT '关联会话ID',
    `role`             VARCHAR(8)   NOT NULL COMMENT '角色：USER / AI',
    `content`          MEDIUMTEXT   NOT NULL COMMENT '消息正文',
    `thinking_content` MEDIUMTEXT   DEFAULT NULL COMMENT 'AI 思考过程',
    `tool_calls`       JSON         DEFAULT NULL COMMENT '工具调用链（JSON数组）',
    `seq`              INT          NOT NULL DEFAULT 0 COMMENT '会话内消息序号',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_seq` (`session_id`, `seq`),
    FULLTEXT INDEX `ft_content` (`content`, `thinking_content`) WITH PARSER ngram
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '聊天消息表';
