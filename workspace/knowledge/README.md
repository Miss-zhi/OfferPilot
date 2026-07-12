# Knowledge Base Directory

> **此目录不存储知识数据** — 所有知识库内容存储在 **Milvus 向量数据库** 中。

## 架构说明

- 知识检索（面试题、面经、教程、答案等）通过子 Agent 调用 `search_questions` / `search_answers` / `search_resources` 工具完成
- 这些工具在后台查询 Milvus 向量数据库进行语义搜索
- 本目录仅保留结构占位，**不包含任何知识文件**

## 如何使用知识库

1. 通过 Web 界面上传文档到知识库
2. 文档经异步入库管道（解析 → 分块 → Embedding）写入 Milvus
3. Agent 通过语义搜索检索知识库内容

## 注意

如果你在此目录下放置文件，Agent 的文件系统工具（如 grep_files）可能找到它们，
但正确的知识检索方式始终是通过向量数据库（Milvus）进行语义搜索。
