---
kind: design
name: 搜索失败链路：Milvus → DB 回退 → Web 兜底
source: session
category: adr
---

# 搜索失败链路：Milvus → DB 回退 → Web 兜底

_来源：663851d → 887af2b 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

## 背景
纯向量检索在冷启动或低覆盖率场景下容易零结果，需要多级兜底保证可用性。

## 决策驱动
- 用户体验（避免空结果）
- 渐进式增强（先向量后文本）
- 外部依赖隔离

## 备选方案
- **Milvus 无结果 → MySQL 全文回退 → WebSearchFallbackService 兜底** — 优点：命中率逐级提升；Web 兜底通过独立 HTTP 端点解耦 AgentScope MCP；缺点：需维护三层结果来源标记 source=milvus/db/web
- **直接走 AgentScope web_search MCP 工具** — 优点：无需自建 HTTP 封装；缺点：Service 层无法直接调用 MCP；耦合 AgentScope 运行时

## 决策
在 KnowledgeBaseService 各搜索方法末尾增加 items.isEmpty() 判断，依次触发 DB 回退和 WebSearchFallbackService 兜底，返回项标记 source 字段区分来源。

## 影响
新增 WebSearchFallbackService 封装对 http://localhost:3000/mcp 的 web_search 调用；后续日志统计可按 source 维度分析命中率。