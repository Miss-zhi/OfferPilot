---
name: query-db
description: 对测试数据库执行只读 SQL 查询，返回结果表格。当用户需要查看数据库数据、验证数据状态或调试 SQL 时使用。禁止执行任何修改数据的操作。
---

# query-db

对 OfferPilot 测试数据库执行只读查询，返回结构化结果。

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

## 安全约束

**仅允许以下语句：**
- `SELECT`
- `SHOW TABLES`
- `SHOW COLUMNS` / `DESCRIBE`
- `SHOW CREATE TABLE`

**严格禁止以下关键词（出现在 SQL 中即拒绝执行）：**
- `INSERT`、`UPDATE`、`DELETE`
- `DROP`、`ALTER`、`TRUNCATE`
- `CREATE`、`RENAME`
- `GRANT`、`REVOKE`

收到修改类请求时，明确告知用户："本 Skill 仅支持只读查询，不允许修改数据。"

## 工作流程

1. 读取 `src/main/resources/application.yml`，提取数据库连接凭证
2. 解析用户意图，生成 SELECT 查询语句
3. 校验 SQL 不含禁止关键词
4. 执行查询命令
5. 将结果以 Markdown 表格形式返回给用户
6. 如查询结果为空，明确告知"无匹配数据"

## 输出格式

查询结果以 Markdown 表格展示，并附带行数统计：

```
查询结果（共 N 行）：

| 列1 | 列2 | 列3 |
|-----|-----|-----|
| 值  | 值  | 值  |
```
