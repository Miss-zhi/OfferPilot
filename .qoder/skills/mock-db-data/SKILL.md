---
name: mock-db-data
description: 对测试数据库执行 INSERT/UPDATE/DELETE 操作来构造测试数据。当用户需要准备测试数据、清空测试数据或在测试场景中模拟业务数据时使用。禁止操作非测试数据。
---

# mock-db-data

对 OfferPilot 测试数据库执行受控的增删改操作，用于构造测试数据和清理测试数据。

## 连接信息

连接凭证从 `src/main/resources/application.yml` 动态读取，字段对应关系：

| 配置字段 | 对应值 |
|---------|--------|
| `spring.datasource.url` | JDBC URL，提取 host、port、database |
| `spring.datasource.username` | 数据库用户名 |
| `spring.datasource.password` | 数据库密码 |

MySQL 客户端：`mysql`（Linux 系统命令）

## 执行命令

先从 application.yml 读取凭证，再拼接命令：

```bash
mysql -h {host} -P {port} -u {username} -p{password} {database} -e "SQL语句"
```

## 安全约束（核心规则）

### 1. 仅允许操作测试数据

所有数据变更必须遵循以下隔离规则之一（二选一）：

**方案 A：按 `create_by` 字段隔离（推荐）**
- INSERT 时，必须设置 `create_by = 'test'`、`update_by = 'test'`
- UPDATE/DELETE 必须带 `WHERE create_by = 'test'` 条件
- 禁止不带 `WHERE create_by = 'test'` 的 UPDATE/DELETE 操作

**方案 B：按 ID 范围隔离**
- 测试数据主键 ID 必须 ≥ 99000
- INSERT 时 ID 需指定 ≥ 99000
- 禁止操作 ID < 99000 的数据行

### 2. 前置检查

- 在执行 UPDATE/DELETE 前，先用等价的 SELECT 预览受影响的数据行数
- 向用户展示将要修改/删除的数据内容，获得用户确认后再执行

### 3. 禁止操作

- 禁止 `DROP TABLE`、`ALTER TABLE`、`TRUNCATE`、`CREATE TABLE` 等 DDL 语句
- 禁止 `GRANT`、`REVOKE` 等权限语句
- 禁止全表 DELETE 不带 WHERE 条件

## 工作流程

1. 读取 `src/main/resources/application.yml`，提取数据库连接凭证
2. 与用户确认要操作的表、操作类型（INSERT / UPDATE / DELETE）和数据内容
3. 检查表是否包含 `create_by` 字段：
   - 含 `create_by` → 采用方案 A（按 `create_by` 隔离）
   - 不含 `create_by` → 采用方案 B（按 ID ≥ 99000 隔离）
4. 执行安全约束检查：
   - 生成 INSERT 语句时，确保 `create_by = 'test'`（若表有该字段）
   - 生成 UPDATE/DELETE 语句时，确保带有隔离条件
   - UPDATE/DELETE 前先执行 SELECT 预览并让用户确认
5. 执行 SQL 变更
6. 返回执行结果（影响行数）

## 使用示例

### INSERT 插入测试数据

```sql
-- 含 create_by 字段的表
INSERT INTO `user` (`name`, `email`, `role`, `create_by`, `update_by`, `created_at`, `updated_at`)
VALUES ('测试用户A', 'test_a@example.com', 'ADMIN', 'test', 'test', 1780000000000, 1780000000000);

-- 不含 create_by 字段的表（ID ≥ 99000）
INSERT INTO `config` (`id`, `config_key`, `config_value`)
VALUES (99001, 'test_key', 'test_value');
```

### UPDATE 更新测试数据

```sql
-- 先预览
SELECT * FROM `user` WHERE `name` = '测试用户A' AND `create_by` = 'test';
-- 再更新
UPDATE `user` SET `email` = 'updated@example.com' WHERE `name` = '测试用户A' AND `create_by` = 'test';
```

### DELETE 清理测试数据

```sql
-- 方案 A
DELETE FROM `user` WHERE `create_by` = 'test';

-- 方案 B
DELETE FROM `config` WHERE `id` >= 99000;
```

## 输出格式

执行完成后返回影响行数：

```
操作成功：
  表名：user
  操作类型：INSERT
  影响行数：1
  隔离方式：create_by = 'test'
```

或预览结果：

```
以下数据将被更新/删除（共 3 行），是否确认执行？

| id | name     | email                  | create_by |
|----|----------|------------------------|-----------|
| 1  | 测试用户A | test_a@example.com     | test      |
| 2  | 测试用户B | test_b@example.com     | test      |
| 3  | 测试用户C | test_c@example.com     | test      |
```
