# RAG 召回策略升级方案

## 目标架构

将现有「Milvus 向量检索 → 无结果才回退 DB」的单路顺序模式改为：

```
Query → Query扩展 → 多路并行召回(CompletableFuture) → RRF融合 → Rerank精排 → 返回Top-N
```

## Task 0: API 端点预验证（前置检查）

- 用 curl 验证 DashScope Rerank API 的 OpenAI 兼容端点是否可用：
  ```bash
  curl -X POST "https://dashscope.aliyuncs.com/compatible-api/v1/reranks" \
    -H "Authorization: Bearer $DASHSCOPE_API_KEY" \
    -H "Content-Type: application/json" \
    -d '{"model":"qwen3-rerank","query":"test","documents":["doc1","doc2"]}'
  ```
- 若 404/不可用 → 改用 DashScope 原生 Rerank API：
  `POST https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank`
- 若原生 API 也不可用 → 降级方案：不引入 Rerank，RRF 融合后直接返回 Top-N（配置 `enabled=false`）

## Task 1: 扩展配置 — AgentScopeProperties 新增 RerankConfig

- 文件: [AgentScopeProperties.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java)
- 新增 `RerankConfig` 静态内部类，字段:
  - `enabled`: 是否启用 rerank（默认 true）
  - `apiKey`: API Key，未配置时回退使用 `agentscope.model.api-key`（与 EmbeddingConfig 一致）
  - `modelName`: 模型名（默认 `qwen3-rerank`，阿里云通义 Rerank 模型）
  - `baseUrl`: API 端点（默认值取决于 Task 0 验证结果）
  - `topN`: 精排后保留数量（默认 5）
  - `scoreThreshold`: 最低分数阈值（默认 0.0，不过滤）
- 在 `AgentScopeProperties` 中添加 `private RerankConfig rerank = new RerankConfig()`
- API Key 回退优先级：`agentscope.rerank.api-key` → `agentscope.model.api-key`（在 RerankerService 中实现）

## Task 2: 新建 RerankerService — DashScope Rerank API 封装

- 新文件: `src/main/java/com/tutorial/offerpilot/service/RerankerService.java`
- 调用 Rerank API（端点由 Task 0 确定）
- 入参: `(String query, List<String> documents, int topN)`
  - 批量限制：DashScope Rerank 单次最多 50 篇文档，需在方法内检查
- 出参: `List<RerankResult>`（包含 index + relevanceScore + document）
- API Key 解析：`agentscope.rerank.api-key` 为空时回退到 `agentscope.model.api-key`
- 失败降级：直接返回原始顺序（按输入 index），不阻断检索链路
- 配置开关：`agentscope.rerank.enabled=false` 时 skip

## Task 3: 改造 VectorSearchService — 新增 RRF 融合 + 距离归一化

- 文件: [VectorSearchService.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java)
- 新增 `fuseWithRRF(List<List<SearchHit>> pathResults, int k)` 方法，k=60
  - 输入：多路召回的各自结果列表（每路内部已按分数排序）
  - RRF 公式：`score(d) = Σ 1/(k + rank_i(d))`，其中 rank 从 1 开始
  - **注意**：RRF 使用排名位置而非原始分数；各路之间尺度不同（Milvus: COSINE 距离升序，MySQL: relevanceScore 降序），但 RRF 不受影响
  - 返回：合并去重后按 RRF score 降序排列的列表
- 新增 `cosineDistanceToScore(float distance)` 方法：`score = 1 - distance/2`
  - 推导：Milvus COSINE 距离 `d ∈ [0,2]`，映射到 `[0,1]`，1=完全匹配
  - 等价于 `(1 + cosine_similarity) / 2`
  - 仅用于最终展示分数，不与 RRF 计算混用
- `searchMultiCollection` 返回的 SearchHit.score 替换为归一化后的 `[0,1]` 分数
  - 同步修改 KnowledgeBaseService 中现有 `1.0f / (1.0f + hit.getScore())` 转换逻辑（不再需要）

## Task 3.5: InterviewQuestionRepository 新增 LIKE 查询

- 文件: [InterviewQuestionRepository.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/repository/InterviewQuestionRepository.java)
- 当前 `findAll()` + 内存过滤性能极差，必须添加 native LIKE 查询替代：
  ```java
  @Query("SELECT q FROM InterviewQuestion q WHERE LOWER(q.questionText) LIKE LOWER(CONCAT('%', :keyword, '%'))")
  List<InterviewQuestion> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
  ```
- 同样添加带答案过滤的版本（用于 searchAnswers 的 MySQL 路径）：
  ```java
  @Query("SELECT q FROM InterviewQuestion q WHERE q.answerText IS NOT NULL AND q.answerText <> '' " +
         "AND LOWER(q.questionText) LIKE LOWER(CONCAT('%', :keyword, '%'))")
  List<InterviewQuestion> searchWithAnswerByKeyword(@Param("keyword") String keyword, Pageable pageable);
  ```

## Task 4: 改造 KnowledgeBaseService — 多路并行召回 + Rerank 集成

- 文件: [KnowledgeBaseService.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java)
- `searchQuestions(SearchRequest req)` 改造为:

  **Phase 1 — 并行召回（CompletableFuture）**：
  1. **Path A** (futureA): Milvus 向量检索（PUBLIC KBs），topK=20
     - 暂不加入 PRIVATE KBs（SearchRequest 当前无 userId，后续版本扩展）
  2. **Path B** (futureB): MySQL InterviewQuestion LIKE（使用 Task 3.5 新增的 Repository 方法），LIMIT=10
     - 使用 `PageRequest.of(0, 10)` 传入
  3. `CompletableFuture.allOf(futureA, futureB).get(timeout)`，单路异常不影响另一路

  **Phase 2 — RRF 融合**：
  1. 调用 `vectorSearchService.fuseWithRRF(List.of(pathAHits, pathBHits), 60)`
  2. 去重（chunk_id 相同取分数高的）

  **Phase 3 — Rerank 精排**：
  1. 取 RRF 融合后的 Top-20 送入 `RerankerService.rerank(query, documents, topN)`
  2. 按 rerank score 排序，过滤低于 `scoreThreshold` 的结果

  **Phase 4 — 输出**：
  1. 返回 `Math.min(req.getTopK(), rerankTopN)` 条（用户请求量和系统精排量取较小值）
  2. relevanceScore 使用 Rerank 返回的分数（若 Rerank 禁用/失败则使用 RRF 分数）

- `searchAnswers(SearchRequest req)` 同样改造：
  - Path A: Milvus（同 searchQuestions）
  - Path B: MySQL `searchWithAnswerByKeyword`（仅查有答案的面试题），LIMIT=10

- `searchCompanyInterviews(SearchRequest req)` 同样改造：
  - Path A: Milvus（按公司名匹配 KB 的 Collection，当前逻辑保留）
  - Path B: MySQL LIKE（按公司名过滤），LIMIT=10

- `searchResources(SearchRequest req)` 改造：
  - 当前无 MySQL 路径，暂不新增（资源类数据主要来自知识库文档）
  - Path A: Milvus（现有逻辑）→ 直接走 Rerank 精排（跳过 RRF，因为只有一路）

- **失败降级**：任一路径异常 catch 后返回空列表，不影响另一路
- **缓存适配**：
  - 缓存 key 更新为 `{keyword}_{category}_{difficulty}_{position}` 组合
  - Rerank 结果也纳入缓存（`@Cacheable` 的 value 不变，key 扩展）
  - `@CacheEvict` 保持不变（文档变更时清空所有搜索缓存）

## Task 5: 启用 SearchRequest 元数据过滤 + 入库管道同步

- 文件: [SearchRequest.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/dto/tool/SearchRequest.java)
- `buildFilterExpr()` 实现过滤逻辑:
  - `category` → `category == "xxx"`
  - `difficulty` → `difficulty == "xxx"`
  - `position` → `position == "xxx"`
  - 多个条件用 `&&` 拼接，无条件返回 `null`（不做过滤）
  - 使用 Milvus 标量过滤语法，字符串值需转义双引号

- 文件: [MilvusCollectionManager.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java)
- `createCollection()` 的 Schema 新增 3 个标量字段（VarChar）：
  - `category` (maxLength=64)
  - `difficulty` (maxLength=16)
  - `position` (maxLength=128)

- 文件: [DocumentIngestionService.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java)
- `doIngestDocument()` 中构建 Milvus 行时，从知识库（KbKnowledgeBase）获取 `category` 写入：
  ```java
  row.put("category", kb.getCategory() != null ? kb.getCategory() : "");
  row.put("difficulty", "");   // 暂不支持文档级 difficulty，后续扩展
  row.put("position", "");      // 暂不支持文档级 position，后续扩展
  ```
- `OUTPUT_FIELDS` 同步新增 `category`, `difficulty`, `position`

## Task 6: 更新配置文件

- 文件: `src/main/resources/application.yml`
- 添加 `agentscope.rerank` 配置段：
  ```yaml
  agentscope:
    rerank:
      enabled: true
      api-key: ${RERANK_API_KEY:${DASHSCOPE_API_KEY:}}
      model-name: qwen3-rerank
      base-url: https://dashscope.aliyuncs.com/compatible-api/v1/reranks  # 待 Task 0 验证后确定
      top-n: 5
      score-threshold: 0.0
  ```
- `application-dev.yml` / `application-prod.yml` 按需覆盖（通常默认值即可）

## Task 7: 测试

- `RerankerService` 单元测试：Mock DashScope API 响应，验证正常返回 + 降级逻辑
- `VectorSearchService.fuseWithRRF()` 单元测试：构造多路结果，验证 RRF 排序正确性
- `VectorSearchService.cosineDistanceToScore()` 单元测试：验证边界值（d=0→1, d=2→0）
- `KnowledgeBaseService.searchQuestions()` 集成测试：验证多路并行 + RRF + Rerank 端到端流程

## 涉及文件清单

| 操作 | 文件 |
|------|------|
| 验证 | 无文件（curl 命令） |
| 修改 | `config/AgentScopeProperties.java` |
| 新建 | `service/RerankerService.java` |
| 修改 | `service/VectorSearchService.java` |
| 修改 | `repository/InterviewQuestionRepository.java` |
| 修改 | `service/KnowledgeBaseService.java` |
| 修改 | `dto/tool/SearchRequest.java` |
| 修改 | `service/MilvusCollectionManager.java` |
| 修改 | `service/ingestion/DocumentIngestionService.java` |
| 修改 | `src/main/resources/application.yml` |
