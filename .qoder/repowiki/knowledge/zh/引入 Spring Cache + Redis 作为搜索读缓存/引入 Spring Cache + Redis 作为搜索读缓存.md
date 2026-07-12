---
kind: design
name: 引入 Spring Cache + Redis 作为搜索读缓存
source: session
category: adr
---

# 引入 Spring Cache + Redis 作为搜索读缓存

_来源：663851d → 887af2b 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

## 背景
搜索是高频读操作，每次都要经过 Embedding + Milvus 向量检索，延迟高且浪费算力。

## 决策驱动
- 降低 P99 延迟
- 减少 Milvus 压力
- 实现简单、侵入小

## 备选方案
- **@Cacheable / @CacheEvict 注解驱动 Redis 缓存** — 优点：零样板代码、TTL 配置集中、与现有 Spring 生态一致；缺点：缓存 key 设计需谨慎；文档入库时需主动失效
- **手写 Guava/Caffeine 本地缓存** — 优点：无额外依赖；缺点：多实例不共享；运维不可观测

## 决策
RedisConfig 启用 @EnableCaching + RedisCacheManagerBuilderCustomizer 设置 5min TTL；4 个搜索方法加 @Cacheable，文档增删改加 @CacheEvict(allEntries=true)。

## 影响
相同 keyword 命中缓存时跳过 Milvus 调用；文档变更后旧缓存自动失效；需关注缓存穿透（空结果是否缓存）。