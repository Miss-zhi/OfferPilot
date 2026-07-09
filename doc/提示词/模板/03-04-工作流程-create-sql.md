# create-sql

## 概念

建表、改表、加索引等数据库变更的标准化流程。

## 演示操作

在 Qoder 输入 /create-skill，然后输入：

帮我创建一个 Skill，名字是 create-sql，功能是生成数据库变更脚本。
流程：分析需求、设计表结构、写 DDL、写回滚脚本。每个变更必须配套回滚脚本。
文件名规则：V版本号_描述.sql
脚本目录：Database/migrations
