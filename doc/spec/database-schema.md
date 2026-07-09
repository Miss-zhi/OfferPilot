# 数据库 Schema 设计（DDL）

> **来源**：《03-详细设计说明书》§4  
> **模块**：数据库设计  
> **数据库**：MySQL 8.0（生产）/ H2（开发），DDL 兼容两者  
> **规模**：18 张表（9 业务 + 4 知识库管理 + 1 检索日志 + 1 Token 黑名单 + 1 定时任务 + 2 认证扩展字段）

---

## 业务表（9 张）

### 1. 用户表 `op_user`

```sql
CREATE TABLE IF NOT EXISTS op_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL UNIQUE,       -- 业务 ID
    username        VARCHAR(128) NOT NULL UNIQUE,       -- 登录用户名
    password_hash   VARCHAR(256) NOT NULL,              -- BCrypt 加密
    email           VARCHAR(256),
    role            VARCHAR(16)  NOT NULL DEFAULT 'USER', -- USER / ADMIN
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,  -- 账号是否启用
    target_company  VARCHAR(128),
    target_position VARCHAR(128),
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 2. 上传文件表 `op_file`

```sql
CREATE TABLE IF NOT EXISTS op_file (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id         VARCHAR(64)  NOT NULL UNIQUE,
    user_id         VARCHAR(64)  NOT NULL,
    file_name       VARCHAR(256) NOT NULL,
    file_path       VARCHAR(512) NOT NULL,
    file_type       VARCHAR(32)  NOT NULL,
    file_size       BIGINT       NOT NULL,
    status          VARCHAR(32)  DEFAULT 'UPLOADED',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);
```

### 3. 面试会话表 `op_interview_session`

```sql
CREATE TABLE IF NOT EXISTS op_interview_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)  NOT NULL UNIQUE,
    user_id         VARCHAR(64)  NOT NULL,
    session_type    VARCHAR(32)  NOT NULL,
    target_company  VARCHAR(128),
    interview_mode  VARCHAR(32),
    overall_score   INT,
    question_count  INT          DEFAULT 0,
    status          VARCHAR(32)  DEFAULT 'ACTIVE',
    started_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    INDEX idx_user_id (user_id)
);
```

### 4. 面试问答明细表 `op_interview_question`

```sql
CREATE TABLE IF NOT EXISTS op_interview_question (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)  NOT NULL,
    question_id     VARCHAR(32),
    question_text   TEXT         NOT NULL,
    answer_text     TEXT,
    tech_score      INT,
    expr_score      INT,
    coverage_score  INT,
    highlights      TEXT,
    weaknesses      TEXT,
    sort_order      INT          DEFAULT 0,
    INDEX idx_session_id (session_id)
);
```

### 5. 分析报告表 `op_analysis_report`

```sql
CREATE TABLE IF NOT EXISTS op_analysis_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id       VARCHAR(64)  NOT NULL UNIQUE,
    user_id         VARCHAR(64)  NOT NULL,
    session_id      VARCHAR(64),
    report_type     VARCHAR(32)  NOT NULL,
    overall_score   INT,
    dimensions_json TEXT,
    details_json    TEXT,
    improvements_json TEXT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);
```

### 6. 学习计划表 `op_study_plan`

```sql
CREATE TABLE IF NOT EXISTS op_study_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    week_start      DATE         NOT NULL,
    week_end        DATE         NOT NULL,
    tasks_json      TEXT         NOT NULL,
    completed_count INT          DEFAULT 0,
    total_count     INT          DEFAULT 0,
    status          VARCHAR(32)  DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);
```

### 7. 知识点掌握度表 `op_knowledge_mastery`

```sql
CREATE TABLE IF NOT EXISTS op_knowledge_mastery (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    knowledge_point VARCHAR(128) NOT NULL,
    category        VARCHAR(64),
    score           INT          NOT NULL,
    previous_score  INT,
    assess_count    INT          DEFAULT 1,
    last_assessed   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_knowledge (user_id, knowledge_point),
    INDEX idx_user_id (user_id)
);
```

### 8. 用户长期记忆表 `op_user_memory`

```sql
-- 替代 MEMORY.md，按 userId 隔离
CREATE TABLE IF NOT EXISTS op_user_memory (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    memory_key      VARCHAR(128) NOT NULL,              -- 记忆标识，如 'target_company'、'weak_point_HashMap'
    memory_content  TEXT         NOT NULL,              -- 记忆内容
    category        VARCHAR(32)  NOT NULL DEFAULT 'GENERAL', -- PROFILE/WEAK_POINT/PREFERENCE/PLAN/GENERAL
    relevance_score FLOAT        DEFAULT 1.0,           -- relevance 权重，用于检索排序
    access_count    INT          DEFAULT 0,             -- 被 Agent 读取的次数
    last_accessed   TIMESTAMP,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_memory (user_id, memory_key),
    INDEX idx_user_id (user_id),
    INDEX idx_category (category)
);
```

### 9. 薪资查询记录表 `op_salary_record`

```sql
CREATE TABLE IF NOT EXISTS op_salary_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    company_name    VARCHAR(128) NOT NULL,
    position        VARCHAR(128),
    base_salary     DECIMAL(10,2),                    -- 月薪（K）
    months          INT,                               -- 月数
    bonus_info      VARCHAR(256),                      -- 奖金信息
    stock_info      VARCHAR(256),                      -- 股票/期权
    other_benefits  TEXT,                              -- 其他福利
    total_package   DECIMAL(10,2),                    -- 总包（万/年）
    source          VARCHAR(128),                      -- 数据来源
    notes           TEXT,                              -- 用户备注
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_company (user_id, company_name)
);
```

---

## 安全相关表（2 张）

### 10. Token 黑名单表 `op_token_blacklist`

```sql
CREATE TABLE IF NOT EXISTS op_token_blacklist (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_jti       VARCHAR(128) NOT NULL UNIQUE,      -- JWT jti 唯一标识
    user_id         VARCHAR(64)  NOT NULL,
    expire_at       TIMESTAMP    NOT NULL,             -- Token 原本的过期时间
    blacklisted_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    reason          VARCHAR(64)  DEFAULT 'LOGOUT',     -- LOGOUT / PASSWORD_CHANGE / ADMIN_REVOKE
    INDEX idx_user_id (user_id),
    INDEX idx_expire_at (expire_at)                    -- 定时清理过期记录
);
```

### 11. 定时任务执行日志表 `op_scheduled_task_log`

```sql
CREATE TABLE IF NOT EXISTS op_scheduled_task_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name       VARCHAR(128) NOT NULL,             -- 任务名称，如 'refresh_company_interviews'
    task_group      VARCHAR(64)  DEFAULT 'DEFAULT',
    status          VARCHAR(32)  NOT NULL,             -- STARTED / SUCCESS / FAILED
    start_time      TIMESTAMP    NOT NULL,
    end_time        TIMESTAMP,
    duration_ms     BIGINT,
    result_summary  TEXT,                              -- 执行结果摘要
    error_message   TEXT,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_name (task_name),
    INDEX idx_start_time (start_time)
);
```

---

## 知识库管理表（4 张）

### 12. 知识库表 `kb_knowledge_base`（含多租户支持）

```sql
CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id           VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    milvus_collection VARCHAR(128) NOT NULL,
    category        VARCHAR(64),
    owner_id        VARCHAR(64),                     -- 创建者 userId，ADMIN 创建公共库时为 NULL
    visibility      VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC', -- PUBLIC / PRIVATE
    document_count  INT          DEFAULT 0,
    chunk_count     INT          DEFAULT 0,
    status          VARCHAR(32)  DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_id),
    INDEX idx_visibility (visibility)
);
```

### 13. 文档表 `kb_document`

```sql
CREATE TABLE IF NOT EXISTS kb_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id          VARCHAR(64)  NOT NULL UNIQUE,
    kb_id           VARCHAR(64)  NOT NULL,
    file_name       VARCHAR(256) NOT NULL,
    file_path       VARCHAR(512) NOT NULL,
    file_type       VARCHAR(32)  NOT NULL,
    file_size       BIGINT       NOT NULL,
    chunk_count     INT          DEFAULT 0,
    chunk_strategy  VARCHAR(64)  DEFAULT 'AUTO',
    status          VARCHAR(32)  DEFAULT 'UPLOADED',
    error_message   TEXT,
    progress        INT          DEFAULT 0,
    metadata_json   TEXT,
    tags            VARCHAR(512),
    uploaded_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    indexed_at      TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_status (status)
);
```

### 14. 分块明细表 `kb_chunk`

```sql
CREATE TABLE IF NOT EXISTS kb_chunk (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id          VARCHAR(64)  NOT NULL,
    kb_id           VARCHAR(64)  NOT NULL,
    chunk_index     INT          NOT NULL,
    content         TEXT         NOT NULL,
    content_hash    VARCHAR(64),
    token_count     INT,
    milvus_offset   BIGINT,
    INDEX idx_doc_id (doc_id),
    INDEX idx_kb_id (kb_id)
);
```

### 15. 检索日志表 `kb_search_log`

```sql
CREATE TABLE IF NOT EXISTS kb_search_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id           VARCHAR(64)  NOT NULL,
    query_text      VARCHAR(2000) NOT NULL,
    filter_expr     VARCHAR(500),
    result_count    INT          DEFAULT 0,
    top_score       FLOAT,
    avg_score       FLOAT,
    latency_ms      INT,
    user_id         VARCHAR(64),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_created_at (created_at)
);
```

---

## 表关系说明

```
op_user (1) ──< (N) op_file               一个用户多个上传文件
op_user (1) ──< (N) op_interview_session   一个用户多个面试会话
op_user (1) ──< (N) op_analysis_report     一个用户多个报告
op_user (1) ──< (N) op_study_plan          一个用户多个周计划
op_user (1) ──< (N) op_knowledge_mastery   一个用户多个知识点追踪
op_user (1) ──< (N) op_user_memory         一个用户多条长期记忆（按 userId 隔离）
op_user (1) ──< (N) op_salary_record       一个用户多条薪资查询记录
op_user (1) ──< (N) op_token_blacklist     一个用户多条 Token 黑名单记录
op_user (1) ──< (N) kb_knowledge_base      一个用户可创建多个私有知识库
op_interview_session (1) ──< (N) op_interview_question  一个会话多个问答

kb_knowledge_base (1) ──< (N) kb_document  一个知识库多个文档
kb_document (1) ──< (N) kb_chunk           一个文档多个分块
kb_knowledge_base (1) ──< (N) kb_search_log 一个知识库多条检索日志
```
