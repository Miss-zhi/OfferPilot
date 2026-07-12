---
kind: design
name: 采用多 Collection 合并检索替代逐库遍历
source: session
category: adr
---

# 采用多 Collection 合并检索替代逐库遍历

_来源：663851d → 887af2b 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

## 背景
原有搜索方法对每个 PUBLIC 知识库逐个调用 Milvus search，导致 N 次网络往返和结果无法统一排序/去重。

## 决策驱动
- 减少 Milvus 调用次数
- 统一排序与去重
- 简化 Service 层循环逻辑

## 备选方案
- **searchMultiCollection 一次性合并检索** — 优点：单次 RPC、结果可全局 topK 排序、代码更简洁；缺点：需要新增聚合服务方法
- **保持 for-loop 逐个 KB 检索** — 优点：改动最小；缺点：N 次网络往返、无法跨库排序、难以做全局去重

## 决策
在 KnowledgeBaseService 中收集所有 PUBLIC KB 的 collectionName，调用 VectorSearchService.searchMultiCollection 一次性检索并合并结果。

## 影响
4 个搜索方法（questions/answers/companyInterviews/resources）均复用同一检索路径；后续元数据过滤、缓存、兜底策略只需维护一处。