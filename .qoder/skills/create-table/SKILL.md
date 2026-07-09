---
name: create-table
description: 生成标准化的建表 SQL 脚本，包含字段定义、索引、注释。输出到 Documents\Sql变更 目录按日期组织。当需要新建数据库表时使用。
---

# 创建数据库表

## 前置规范

- `create_by` / `update_by` 字段类型为 **VARCHAR(50)**，默认值 `'SYSTEM'`
- `created_at` / `updated_at` 字段类型为 **BIGINT**，存储毫秒时间戳
- `id` 统一使用 `BIGINT AUTO_INCREMENT`，作为第一个字段
- 字段顺序：业务字段 → `create_by` → `update_by` → `created_at` → `updated_at` → PRIMARY KEY
- 所有字段必须带 `COMMENT`
- 表必须带 `COMMENT`

## 输出规则

- **输出目录**: `Documents\Sql变更\{YYYYMMDD}\`
- **文件命名**: 按序号前缀，如 `step0_create_{table_name}.sql`、`step1_create_{table_name}.sql`
- **日期格式**: 取当前系统时间，YYYYMMDD
- **版权注释**: 文件顶部必须包含 `# Copyright (c) 2020-06-29 Qoder. All rights reserved.`

## 建表模板

```sql
# Copyright (c) 2020-06-29 Qoder. All rights reserved.

CREATE TABLE `{table_name}` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `{field1}`      {type}({length}) NOT NULL                 COMMENT '字段说明',
    `{field2}`      {type}({length}) DEFAULT NULL             COMMENT '字段说明',
    `create_by`     VARCHAR(50)     DEFAULT 'SYSTEM'          COMMENT '创建人',
    `update_by`     VARCHAR(50)     DEFAULT 'SYSTEM'          COMMENT '更新人',
    `created_at`    BIGINT          NOT NULL                   COMMENT '创建时间（毫秒时间戳）',
    `updated_at`    BIGINT          NOT NULL                   COMMENT '更新时间（毫秒时间戳）',
    PRIMARY KEY (`id`),
    KEY `idx_{table_abbr}_{field}` (`field_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表说明';
```

## 测试数据隔离说明

- 测试环境 INSERT 时 `create_by = 'test'`
- 测试清理时 `DELETE WHERE create_by = 'test'`
- 业务数据默认 `create_by = 'SYSTEM'`

## ALTER TABLE 模板

当需要修改已有表结构时，使用 ALTER TABLE 脚本：

```sql
# Copyright (c) 2020-06-29 Qoder. All rights reserved.

ALTER TABLE `{table_name}`
    ADD COLUMN `{field_name}` {type}({length}) DEFAULT NULL COMMENT '字段说明' AFTER `{after_field}`;
```

## 工作流

1. 确认表名和业务含义
2. 确认业务字段列表（名称、类型、长度、注释）
3. `create_by` / `update_by`（VARCHAR(50) DEFAULT 'SYSTEM'）自动追加到业务字段之后
4. `created_at` / `updated_at`（BIGINT 毫秒时间戳）自动追加到最后两个字段
5. 生成 CREATE TABLE DDL
6. 输出到 `Documents\Sql变更\{YYYYMMDD}\` 目录

## 约束

- 不得创建不带 `COMMENT` 的字段
- `id` 必须为第一个字段，字段顺序为：业务字段 → `create_by` → `update_by` → `created_at` → `updated_at`
- 文件名必须有序号前缀（step0, step1, step2...），避免执行顺序歧义
- 必须包含版权注释
