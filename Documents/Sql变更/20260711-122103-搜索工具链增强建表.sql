-- Copyright (c) 2020-06-29 Qoder. All rights reserved.
-- 面试搜索工具链增强 — 新增表
-- 日期: 2026-07-11

-- 搜索反馈表
CREATE TABLE IF NOT EXISTS op_search_feedback (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    user_id         VARCHAR(64)     NOT NULL                    COMMENT '用户ID',
    query_text      VARCHAR(500)    NOT NULL                    COMMENT '原始查询词',
    tool_name       VARCHAR(64)     NOT NULL                    COMMENT '调用的工具名',
    result_source   VARCHAR(32)     NOT NULL                    COMMENT '结果来源: kb/db/web',
    result_count    INT             NOT NULL DEFAULT 0           COMMENT '返回结果数',
    helpful         TINYINT         DEFAULT NULL                COMMENT '是否有用: 1=有用 0=无用 NULL=未知',
    session_id      VARCHAR(64)     DEFAULT NULL                COMMENT '关联会话ID',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by       VARCHAR(64)     DEFAULT NULL                COMMENT '创建人',
    update_by       VARCHAR(64)     DEFAULT NULL                COMMENT '更新人',
    INDEX idx_sf_user_id (user_id),
    INDEX idx_sf_query_text (query_text(100)),
    INDEX idx_sf_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索反馈记录';

-- 搜索工具链日志表
CREATE TABLE IF NOT EXISTS op_search_tool_log (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    user_id         VARCHAR(64)     DEFAULT NULL                COMMENT '用户ID（匿名搜索可为空）',
    query_text      VARCHAR(500)    NOT NULL                    COMMENT '原始查询词',
    expanded_queries TEXT           DEFAULT NULL                COMMENT 'Query扩展后的多个检索词（JSON数组）',
    tool_name       VARCHAR(64)     NOT NULL                    COMMENT '调用的工具名',
    intent          VARCHAR(32)     DEFAULT NULL                COMMENT '意图分类: practice/learn/company/salary',
    milvus_hits     INT             NOT NULL DEFAULT 0           COMMENT 'Milvus命中数',
    db_hits         INT             NOT NULL DEFAULT 0           COMMENT 'DB回退命中数',
    web_hits        INT             NOT NULL DEFAULT 0           COMMENT 'MCP联网命中数',
    total_results   INT             NOT NULL DEFAULT 0           COMMENT '最终返回结果数',
    zero_result     INT             NOT NULL DEFAULT 0           COMMENT '是否零结果: 1=是',
    latency_ms      BIGINT          NOT NULL DEFAULT 0           COMMENT '搜索耗时（毫秒）',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by       VARCHAR(64)     DEFAULT NULL                COMMENT '创建人',
    update_by       VARCHAR(64)     DEFAULT NULL                COMMENT '更新人',
    INDEX idx_stl_user_id (user_id),
    INDEX idx_stl_query_text (query_text(100)),
    INDEX idx_stl_zero_result (zero_result),
    INDEX idx_stl_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='搜索工具链日志';
