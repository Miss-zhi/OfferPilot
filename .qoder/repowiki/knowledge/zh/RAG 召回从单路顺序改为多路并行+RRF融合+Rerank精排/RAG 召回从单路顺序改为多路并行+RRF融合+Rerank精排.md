---
kind: design
name: RAG 召回从单路顺序改为多路并行+RRF融合+Rerank精排
source: session
category: adr
---

# RAG 召回从单路顺序改为多路并行+RRF融合+Rerank精排

_来源：a219d10 → 1e2013b 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

**状态：** accepted

## 背景
现有检索链路是 Milvus 向量检索 → 无结果才回退 MySQL LIKE 查询的单路顺序模式，召回质量受限于单一向量相似度分数，无法兼顾语义匹配与关键词精确匹配。

## 决策驱动
- 提升召回准确率
- 兼顾语义与关键词匹配
- 可配置的精排开关

## 备选方案
- **保持单路 Milvus 向量检索** _（已否决）_ — 优点：实现简单，延迟低；缺点：召回质量受限，无法处理关键词精确匹配
- **Milvus + MySQL 并行召回 + RRF 融合 + DashScope Rerank 精排** — 优点：向量路径捕获语义相似，MySQL LIKE 路径捕获关键词精确匹配；RRF 融合两路结果；Rerank 做最终排序；失败可降级；缺点：增加 RerankerService 依赖；引入额外网络延迟；需维护 Milvus Schema 新增标量字段

## 决策
将 KnowledgeBaseService.searchQuestions/searchAnswers/searchCompanyInterviews/searchResources 改造为：Path A 走 Milvus 向量检索（topK=20），Path B 走 MySQL InterviewQuestion LIKE 关键词检索，两者并行执行后通过 RRF（Reciprocal Rank Fusion, k=60）融合，取 Top-20 送入新建的 RerankerService 调用 DashScope qwen3-rerank API 精排，最终返回 Top-5（agentscope.rerank.topN 可配）。任一路径异常不阻断其他路径，Rerank 失败则回退到 RRF 融合结果。

## 影响
召回质量显著提升，尤其是对关键词精确匹配的覆盖；但引入了 DashScope Rerank API 的外部依赖和网络延迟；需要在 Milvus Collection 中新增 category/difficulty/position 三个 VarChar 标量字段以支持元数据过滤；可通过 agentscope.rerank.enabled=false 关闭精排进行降级。