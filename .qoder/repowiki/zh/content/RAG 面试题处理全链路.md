# RAG 面试题处理全链路

<cite>
**本文引用的文件列表**
- [Documents/03-详细设计说明书.md](file://Documents/03-详细设计说明书.md)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java)
- [src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java](file://src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java)
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java)
- [src/main/resources/application.yml](file://src/main/resources/application.yml)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java)
- [src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java](file://src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java)
- [src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java](file://src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java)
- [src/main/java/com/tutorial/offerpilot/service/RerankerService.java](file://src/main/java/com/tutorial/offerpilot/service/RerankerService.java)
- [src/main/java/com/tutorial/offerpilot/entity/KbChunk.java](file://src/main/java/com/tutorial/offerpilot/entity/KbChunk.java)
- [src/main/java/com/tutorial/offerpilot/entity/KbDocument.java](file://src/main/java/com/tutorial/offerpilot/entity/KbDocument.java)
- [src/main/java/com/tutorial/offerpilot/entity/KbKnowledgeBase.java](file://src/main/java/com/tutorial/offerpilot/entity/KbKnowledgeBase.java)
- [src/main/java/com/tutorial/offerpilot/controller/FileUploadController.java](file://src/main/java/com/tutorial/offerpilot/controller/FileUploadController.java)
- [src/main/java/com/tutorial/offerpilot/controller/KnowledgeBaseController.java](file://src/main/java/com/tutorial/offerpilot/controller/KnowledgeBaseController.java)
- [src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java)
- [src/main/java/com/tutorial/offerpilot/dto/tool/SearchRequest.java](file://src/main/java/com/tutorial/offerpilot/dto/tool/SearchRequest.java)
- [src/main/java/com/tutorial/offerpilot/service/QueryExpansionService.java](file://src/main/java/com/tutorial/offerpilot/service/QueryExpansionService.java)
- [src/main/java/com/tutorial/offerpilot/service/SearchAnalyticsService.java](file://src/main/java/com/tutorial/offerpilot/service/SearchAnalyticsService.java)
- [src/main/java/com/tutorial/offerpilot/entity/SearchToolLog.java](file://src/main/java/com/tutorial/offerpilot/entity/SearchToolLog.java)
- [src/main/java/com/tutorial/offerpilot/entity/SearchFeedback.java](file://src/main/java/com/tutorial/offerpilot/entity/SearchFeedback.java)
- [src/main/java/com/tutorial/offerpilot/repository/SearchToolLogRepository.java](file://src/main/java/com/tutorial/offerpilot/repository/SearchToolLogRepository.java)
- [src/main/java/com/tutorial/offerpilot/controller/SearchStatsController.java](file://src/main/java/com/tutorial/offerpilot/controller/SearchStatsController.java)
- [src/main/java/com/tutorial/offerpilot/config/RedisConfig.java](file://src/main/java/com/tutorial/offerpilot/config/RedisConfig.java)
- [src/main/java/com/tutorial/offerpilot/service/SearchResultCacheService.java](file://src/main/java/com/tutorial/offerpilot/service/SearchResultCacheService.java)
</cite>

## 更新摘要
**变更内容**   
- 移除了 PersonalizedRankService 类及其相关的个性化排序功能（删除个性化权重计算逻辑）
- 新增 RerankerService 实现，支持基于 DashScope Rerank API 的语义重排序精排
- 升级 VectorSearchService 的 RRF 融合算法和多路并行召回机制
- KnowledgeBaseService 集成全新的两阶段多路召回 + RRF 融合 + Rerank 精排架构
- 搜索流程从单路顺序模式升级为行业标准的双阶段并行召回架构

## 目录
- RAG 全链路架构
- 离线阶段：数据预处理与入库
- 离线阶段：向量索引构建
- 在线阶段：多路召回与精排
- 搜索工具链增强架构
- RAG核心Bug修复

## RAG 全链路架构
> 绘制离线入库 + 在线检索的双阶段 Mermaid 流程图

```mermaid
graph TB
subgraph "离线阶段"
A["上传文档<br/>FileUploadController / KnowledgeBaseController"] --> B["创建文档记录<br/>KnowledgeBaseService.createDoc()"]
B --> C["异步入库管道<br/>DocumentIngestionService.ingestDocument()"]
C --> D["解析文本<br/>DocumentParser.parse()"]
C --> E["分块策略<br/>DocumentChunker.chunk()"]
C --> F["生成向量<br/>EmbeddingService.embedBatch()"]
C --> G["写入 Milvus<br/>MilvusClientV2.insert()"]
C --> H["持久化分块<br/>KbChunk 表"]
end
subgraph "在线阶段"
Q["用户提问"] --> I["多租户检索范围<br/>PUBLIC + PRIVATE 知识库"]
I --> J["多路并行召回<br/>Milvus向量 + MySQL LIKE"]
J --> K["RRF 融合去重<br/>Reciprocal Rank Fusion"]
K --> L["Rerank 精排<br/>DashScope语义重排序"]
L --> M["上下文增强 + 动态生成面试题"]
end
G -.->|集合名映射| I
H -.->|doc_id/chunk_index 关联| K
```

图示来源
- [src/main/java/com/tutorial/offerpilot/controller/FileUploadController.java:1-49](file://src/main/java/com/tutorial/offerpilot/controller/FileUploadController.java#L1-L49)
- [src/main/java/com/tutorial/offerpilot/controller/KnowledgeBaseController.java:1-168](file://src/main/java/com/tutorial/offerpilot/controller/KnowledgeBaseController.java#L1-L168)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:204-240](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L204-L240)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java:46-145](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java#L46-L145)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java:29-37](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java#L29-L37)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java:25-43](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java#L25-L43)
- [src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java:62-74](file://src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java#L62-L74)
- [src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java:85-122](file://src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java#L85-L122)

章节来源
- [Documents/03-详细设计说明书.md:42-109](file://Documents/03-详细设计说明书.md#L42-L109)
- [src/main/java/com/tutorial/offerpilot/controller/FileUploadController.java:28-47](file://src/main/java/com/tutorial/offerpilot/controller/FileUploadController.java#L28-L47)
- [src/main/java/com/tutorial/offerpilot/controller/KnowledgeBaseController.java:90-109](file://src/main/java/com/tutorial/offerpilot/controller/KnowledgeBaseController.java#L90-L109)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:204-240](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L204-L240)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java:46-145](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java#L46-L145)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java:29-37](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java#L29-L37)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java:25-43](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java#L25-L43)
- [src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java:62-74](file://src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java#L62-L74)
- [src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java:85-122](file://src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java#L85-L122)

## 离线阶段：数据预处理与入库
> 展示 5 阶段异步入库管道的 Mermaid 状态图（UPLOADED→PARSING→CHUNKING→EMBEDDING→INDEXING→ACTIVE）
> 说明 DocumentParser 的 4 种格式解析（PDF/PDFBox, DOCX/POI, MD, TXT）
> 说明 DocumentChunker 的 4 种分块策略及自动检测逻辑
> 说明 EmbeddingService 的配置管理增强与 DashScope text-embedding-v3 调用（单条/批量，最多 25 条/次）
> 展示 DocumentIngestionService 的核心入库代码流程

```mermaid
stateDiagram-v2
[*] --> UPLOADED : "创建文档记录"
UPLOADED --> PARSING : "开始解析"
PARSING --> CHUNKING : "解析完成"
CHUNKING --> EMBEDDING : "分块完成"
EMBEDDING --> INDEXING : "向量生成完成"
INDEXING --> ACTIVE : "写入成功"
PARSING --> FAILED : "解析异常"
CHUNKING --> FAILED : "分块为空或异常"
EMBEDDING --> FAILED : "Embedding 失败"
INDEXING --> FAILED : "写入失败"
FAILED --> [*]
ACTIVE --> [*]
```

- 文档解析器支持四种格式：Markdown、TXT、PDF（PDFBox）、DOCX（Apache POI）。解析后输出纯文本，供后续分块使用。
- 分块策略包含 AUTO、BY_QUESTION、BY_HEADING、BY_SIZE。AUTO 会统计"---"分隔符和 Markdown 标题数量，自动选择最合适的策略；BY_QUESTION 按"---"切题；BY_HEADING 按 #/##/### 切节；BY_SIZE 为固定大小滑动窗口兜底。
- **更新** Embedding 服务现已支持独立的配置管理，通过 `agentscope.embedding.*` 配置项进行设置。API Key 采用智能解析机制：优先使用 `embedding.api-key`，未配置时自动回退到 `model.api-key`。支持动态 URL 配置，默认调用 DashScope text-embedding-v3 API，提供单条与批量接口，批量上限为 25 条/次，内部自动分批。
- 异步入库管道由 @Async 驱动，顺序执行 PARSING → CHUNKING → EMBEDDING → INDEXING → ACTIVE，任一阶段异常均置为 FAILED 并记录错误信息。

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java:29-37](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentParser.java#L29-L37)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java:25-43](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentChunker.java#L25-L43)
- [src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java:37-58](file://src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java#L37-L58)
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java:58-66](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java#L58-L66)
- [src/main/resources/application.yml:57-63](file://src/main/resources/application.yml#L57-L63)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java:46-145](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java#L46-L145)

## 离线阶段：向量索引构建
> 说明 Milvus Collection 的动态创建（通用 Schema + IVF_FLAT 索引）
> 说明文档与 Milvus offset 的双向映射（kb_chunk.milvus_offset）
> 说明重建索引流程（delete by doc_id + re-ingest）

- **更新** 动态创建 Collection：在创建知识库时，系统现在会自动为每个知识库分配一个独立的 Milvus Collection，名称形如 kb_{kbId}。Schema 包含 id（自增主键）、doc_id、chunk_index、content、vector（1024 维浮点向量）等字段。**关键改进**：Collection 创建后会自动创建 IVF_FLAT 索引（nlist=128）并加载到内存中，确保首次查询即可正常响应。
- 双向映射：入库时将 KbChunk 的 milvus_offset 保存至数据库，用于后续删除或定位向量；同时 Milvus 行中保留 doc_id 与 chunk_index，便于按文档维度进行过滤与重建。
- 重建索引：先按 doc_id 条件删除旧向量，再删除 DB 中的对应分块，重置文档状态为 UPLOADED，最后重新触发入库管道完成重索引。

```mermaid
sequenceDiagram
participant Admin as "管理员/用户"
participant KB as "KnowledgeBaseController"
participant Service as "KnowledgeBaseService.reindexDoc()"
participant Milvus as "MilvusClientV2"
participant Ingest as "DocumentIngestionService"
Admin->>KB : POST /{kbId}/docs/{docId}/reindex
KB->>Service : reindexDoc(kbId, docId, userId)
Service->>Milvus : delete(filter="doc_id == '{docId}'")
Service->>Service : 删除DB分块并更新统计
Service->>Service : 重置文档状态为 UPLOADED
Service->>Ingest : ingestDocument(docId)
Ingest-->>Admin : 进度轮询返回 ACTIVE
```

图示来源
- [src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java:34-78](file://src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java#L34-78)
- [src/main/java/com/tutorial/offerpilot/entity/KbChunk.java:38-39](file://src/main/java/com/tutorial/offerpilot/entity/KbChunk.java#L38-39)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:509-558](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L509-558)
- [src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java:46-145](file://src/main/java/com/tutorial/offerpilot/service/ingestion/DocumentIngestionService.java#L46-L145)

章节来源
- [src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java:34-78](file://src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java#L34-78)
- [src/main/java/com/tutorial/offerpilot/entity/KbChunk.java:38-39](file://src/main/java/com/tutorial/offerpilot/entity/KbChunk.java#L38-39)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:509-558](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L509-558)

## 在线阶段：多路召回与精排
> 绘制全新两阶段多路召回 + RRF 融合 + Rerank 精排的 Mermaid 流程图

```mermaid
flowchart TD
Start(["查询入口"]) --> Phase1["Phase 1: 多路并行召回"]
Phase1 --> PathA["Path A: Milvus 向量检索<br/>PUBLIC + 用户 PRIVATE KBs"]
Phase1 --> PathB["Path B: MySQL LIKE 关键词检索"]
PathA --> Parallel["异步并行执行"]
PathB --> Parallel
Parallel --> Phase2["Phase 2: RRF 融合"]
Phase2 --> RRF["Reciprocal Rank Fusion<br/>k=60 标准值"]
RRF --> Merge["合并去重 + 排名融合"]
Merge --> Phase3["Phase 3: Rerank 精排"]
Phase3 --> Rerank["DashScope Rerank API<br/>qwen3-rerank 模型"]
Rerank --> Filter{"分数阈值过滤<br/>scoreThreshold"}
Filter --> |是| Threshold["过滤低分结果"]
Filter --> |否| Skip["跳过过滤"]
Threshold --> TopN["取 Top-N 结果"]
Skip --> TopN
TopN --> End(["返回给上层 Agent/工具层"])
```

**更新** 系统已升级为行业标准的两阶段多路召回架构：

### 第一阶段：多路并行召回
- **Path A**: Milvus 向量检索，同时检索 PUBLIC 知识库和用户私有知识库，topK=20
- **Path B**: MySQL InterviewQuestion LIKE 关键词检索，不再是回退路径而是并行路径，dbLimit=10
- **异步执行**: 使用 CompletableFuture.supplyAsync 实现真正的并行召回，提升整体性能
- **容错机制**: 任一路径异常不影响其他路径，getQuietly 方法确保单路失败返回空列表

### 第二阶段：RRF 融合
- **算法原理**: Reciprocal Rank Fusion (RRF)，公式 score(d) = Σ 1/(k + rank_i(d))，其中 k=60
- **优势**: 使用排名位置而非原始分数进行融合，不受各路 score 尺度差异影响
- **去重策略**: 基于 docId + chunkIndex 作为唯一标识进行去重
- **分数替换**: 用 RRF score 替换原始相似度分数作为融合后的相关性分数

### 第三阶段：Rerank 精排
- **语义重排序**: 调用 DashScope qwen3-rerank 模型对 Top-20 候选进行语义相关性精排
- **配置开关**: agentscope.rerank.enabled=false 时跳过精排，直接返回 RRF 融合结果
- **降级策略**: API 调用失败时保持原始顺序，不阻断检索链路
- **阈值过滤**: 可配置 scoreThreshold 过滤低相关性结果

```mermaid
classDiagram
class KnowledgeBaseService {
+searchQuestions(SearchRequest req)
+searchAnswers(SearchRequest req)
+searchCompanyInterviews(SearchRequest req)
+searchResources(SearchRequest req)
-milvusRecall(query, topK, filterExpr, userId)
-mysqlRecallQuestions(keyword, limit)
-doRerank(query, fusedCandidates)
}
class VectorSearchService {
+searchMultiCollection(collections, query, topK, finalTopK)
+fuseWithRRF(pathResults, k)
+cosineDistanceToScore(distance)
}
class RerankerService {
+rerank(query, documents, topN)
-RerankResult[index, relevanceScore, document]
-callRerankApi(query, documents)
-fallbackOrder(documents)
}
class AgentScopeProperties {
+RerankConfig enabled, apiKey, modelName
+baseUrl, topN, scoreThreshold
+connectTimeout, readTimeout
}
KnowledgeBaseService --> VectorSearchService : "多路召回 + RRF融合"
KnowledgeBaseService --> RerankerService : "语义精排"
KnowledgeBaseService --> AgentScopeProperties : "读取Rerank配置"
VectorSearchService --> RerankerService : "间接依赖"
```

**图示来源**
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:204-240](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L204-L240)
- [src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java:134-177](file://src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java#L134-L177)
- [src/main/java/com/tutorial/offerpilot/service/RerankerService.java:111-151](file://src/main/java/com/tutorial/offerpilot/service/RerankerService.java#L111-L151)
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java:113-131](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java#L113-L131)

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:204-240](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L204-L240)
- [src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java:134-177](file://src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java#L134-L177)
- [src/main/java/com/tutorial/offerpilot/service/RerankerService.java:111-151](file://src/main/java/com/tutorial/offerpilot/service/RerankerService.java#L111-L151)
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java:113-131](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java#L113-L131)

## 搜索工具链增强架构

> 详细说明搜索工具链的架构设计，包括统一智能搜索入口、多维度过滤、智能查询扩展和语义重排序

### 统一智能搜索入口 - SmartSearchTool

**更新** SmartSearchTool 作为统一的智能搜索入口，替代多个独立的 search_* 工具，提供单一 smart_search 方法。该工具内部自动完成意图分类、Query 扩展、多路召回和结果整合。

```mermaid
flowchart TD
UserInput["用户自然语言查询"] --> Intent["意图分类<br/>company/practice/learn/general"]
Intent --> Expand["Query 扩展<br/>LLM + 规则模式"]
Expand --> MultiRoute["多路召回路由"]
MultiRoute --> Company["公司调研<br/>searchCompanyInterviews()"]
MultiRoute --> Practice["刷题练习<br/>searchQuestions()"]
MultiRoute --> Learn["学习资源<br/>searchResources()"]
MultiRoute --> General["综合搜索<br/>并行搜索所有类型"]
Company --> Merge["结果合并"]
Practice --> Merge
Learn --> Merge
General --> Merge
Merge --> Sort["相关性排序"]
Sort --> Dedup["去重限制"]
Dedup --> Result["统一搜索结果"]
```

**图示来源**
- [src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java:39-157](file://src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java#L39-157)

**章节来源**
- [src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java:1-205](file://src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java#L1-L205)

### 多维度过滤 - SearchRequest 对象

**更新** 引入结构化的 SearchRequest 对象，替代单一的 keyword 字符串，支持多维度过滤和个性化搜索。

| 字段 | 类型 | 描述 | 示例值 |
|------|------|------|--------|
| keywords | String | 搜索关键词，多个用空格分隔 | "Java并发 多线程" |
| category | String | 分类过滤 | "专业技能/项目经验/情景分析" |
| difficulty | String | 难度过滤 | "easy/medium/hard" |
| company | String | 公司名称过滤 | "字节跳动/阿里巴巴" |
| position | String | 岗位名称过滤 | "后端开发/算法工程师" |
| topK | Integer | 返回数量，默认10，最大50 | 10 |
| userId | String | 用户ID（用于日志记录） | "user-123" |

**章节来源**
- [src/main/java/com/tutorial/offerpilot/dto/tool/SearchRequest.java:1-71](file://src/main/java/com/tutorial/offerpilot/dto/tool/SearchRequest.java#L1-L71)

### Query 扩展服务 - QueryExpansionService

**更新** QueryExpansionService 服务，通过 DashScope LLM 将短关键词扩展为多条检索短语，提升召回率和多样性。

- **LLM 扩展模式**：使用 qwen-turbo 轻量模型，将输入 "Java并发" 扩展为 ["Java并发面试题", "线程池原理", "volatile关键字"]
- **规则模式兜底**：当 LLM 调用失败时，自动回退到规则模式，通过关键词拆分和常见后缀追加生成扩展词
- **配置开关**：通过 `agentscope.search.query-expansion.enabled=true` 控制是否启用

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/QueryExpansionService.java:1-214](file://src/main/java/com/tutorial/offerpilot/service/QueryExpansionService.java#L1-L214)

### 语义重排序 - RerankerService

**新增** RerankerService 服务，基于 DashScope Rerank API 对多路召回结果进行语义相关性重排序，显著提升最终结果的精准度。

- **模型支持**：默认使用 qwen3-rerank 模型，支持 OpenAI 兼容端点
- **API Key 优先级**：rerank.api-key > model.api-key（回退兼容），无有效 Key 时自动禁用
- **失败降级**：API 调用失败时保持原始顺序，不阻断检索链路
- **配置开关**：agentscope.rerank.enabled=false 时跳过精排，直接返回 RRF 融合结果
- **阈值过滤**：可配置 scoreThreshold 过滤低相关性结果

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/RerankerService.java:1-213](file://src/main/java/com/tutorial/offerpilot/service/RerankerService.java#L1-L213)

### 搜索分析与统计

**更新** 完整的搜索分析和统计功能，包括搜索日志记录、反馈收集和统计分析。

#### 搜索日志记录
- **SearchToolLog 实体**：记录每次搜索的详细指标，包括各来源命中数和耗时
- **SearchAnalyticsService**：提供搜索日志持久化和统计分析接口
- **SearchStatsController**：暴露 `/api/v1/kb/search/stats` 统计接口

#### 搜索反馈收集
- **SearchFeedback 实体**：追踪用户是否采纳搜索结果，用于评估搜索质量
- **反馈字段**：包含 queryText、toolName、resultSource、helpful 等关键信息

#### 数据库表结构
- **op_search_tool_log**：搜索工具链日志表
- **op_search_feedback**：搜索反馈记录表

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/SearchAnalyticsService.java:1-138](file://src/main/java/com/tutorial/offerpilot/service/SearchAnalyticsService.java#L1-L138)
- [src/main/java/com/tutorial/offerpilot/entity/SearchToolLog.java:1-60](file://src/main/java/com/tutorial/offerpilot/entity/SearchToolLog.java#L1-L60)
- [src/main/java/com/tutorial/offerpilot/entity/SearchFeedback.java:1-47](file://src/main/java/com/tutorial/offerpilot/entity/SearchFeedback.java#L1-L47)
- [src/main/java/com/tutorial/offerpilot/repository/SearchToolLogRepository.java:1-21](file://src/main/java/com/tutorial/offerpilot/repository/SearchToolLogRepository.java#L1-L21)
- [src/main/java/com/tutorial/offerpilot/controller/SearchStatsController.java:1-34](file://src/main/java/com/tutorial/offerpilot/controller/SearchStatsController.java#L1-L34)

### 简化的搜索流程

**更新** KnowledgeBaseService 中的搜索方法现已升级为全新的两阶段多路召回架构，移除了个性化排序逻辑：

```mermaid
sequenceDiagram
participant User as "用户"
participant KB as "KnowledgeBaseService"
participant VS as "VectorSearchService"
participant DB as "MySQL"
participant RR as "RerankerService"
User->>KB : searchQuestions(SearchRequest)
KB->>VS : 并行召回：Milvus向量检索
KB->>DB : 并行召回：LIKE关键词检索
alt 并行执行
VS-->>KB : 向量匹配结果
DB-->>KB : 数据库匹配结果
end
KB->>VS : RRF融合去重
VS-->>KB : 融合结果
KB->>RR : Rerank语义精排
RR-->>KB : 精排结果
KB-->>User : 最终搜索结果
```

**图示来源**
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:204-240](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L204-L240)

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:204-240](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L204-L240)

## RAG核心Bug修复

> 详细说明RAG系统中三个阻断性Bug的修复方案和实施细节

### Bug #1 + #2：MilvusCollectionManager 集成到 KnowledgeBaseService

**修复内容**：解决了知识库创建时Milvus Collection未自动创建和加载的问题

- **构造函数注入**：KnowledgeBaseService 构造函数新增第13个参数 `MilvusCollectionManager milvusCollectionManager`
- **同步创建流程**：在 `createKnowledgeBase()` 方法中，`kbRepo.save(kb)` 之后立即调用 `milvusCollectionManager.createCollection(collectionName)`
- **自动索引创建**：Collection 创建时自动创建 IVF_FLAT 索引（nlist=128），metricType=COSINE
- **内存加载**：Collection 创建后立即加载到内存，确保首次查询即可正常响应

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:65-91](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L65-L91)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:116-123](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L116-L123)
- [src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java:37-97](file://src/main/java/com/tutorial/offerpilot/service/MilvusCollectionManager.java#L37-L97)

### Bug #3：SearchRequest.buildFilterExpr() 字段不存在

**修复内容**：移除了对不存在的category和difficulty字段的引用，避免Milvus查询错误

- **问题原因**：category和difficulty字段尚未加入Milvus Collection Schema
- **修复方案**：`buildFilterExpr()` 方法现在返回null，禁用Milvus标量过滤
- **降级策略**：改为在应用层进行后过滤，保证功能可用性
- **未来规划**：待Schema扩展后恢复过滤逻辑

**章节来源**
- [src/main/java/com/tutorial/offerpilot/dto/tool/SearchRequest.java:42-44](file://src/main/java/com/tutorial/offerpilot/dto/tool/SearchRequest.java#L42-L44)

### 多Collection合并检索优化

**优化内容**：将逐个KB遍历的旧逻辑替换为一次性多Collection合并检索

- **性能提升**：从N次独立检索优化为1次批量检索
- **统一接口**：所有搜索方法（searchQuestions、searchAnswers、searchCompanyInterviews、searchResources）统一使用 `searchMultiCollection`
- **容错机制**：单个Collection检索失败不影响其他Collection的检索结果

**章节来源**
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:196-221](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L196-L221)
- [src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java:85-122](file://src/main/java/com/tutorial/offerpilot/service/VectorSearchService.java#L85-L122)

### Redis缓存机制增强

**增强内容**：为搜索接口添加5分钟TTL缓存，提升查询性能

- **缓存配置**：RedisConfig中为四个搜索方法分别配置5分钟TTL
- **注解使用**：搜索方法添加 `@Cacheable` 注解，文档操作添加 `@CacheEvict` 注解
- **缓存策略**：相同查询条件直接返回缓存结果，减少重复计算

**章节来源**
- [src/main/java/com/tutorial/offerpilot/config/RedisConfig.java:29-48](file://src/main/java/com/tutorial/offerpilot/config/RedisConfig.java#L29-48)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:176-182](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L176-182)
- [src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java:694-722](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java#L694-L722)

## 新增：Embedding 配置管理增强

> 详细说明新的独立配置架构和智能 API Key 解析机制

### 配置结构概览

**更新** EmbeddingService 现已实现完全独立的配置管理，不再依赖 LLM Model 配置。新的配置架构通过 `AgentScopeProperties.EmbeddingConfig` 类实现，支持以下核心特性：

```mermaid
graph LR
subgraph "配置层次结构"
A[AgentScopeProperties] --> B[ModelConfig - LLM模型配置]
A --> C[EmbeddingConfig - 独立嵌入配置]
A --> D[KnowledgeConfig - 知识库配置]
A --> E[RerankConfig - 独立重排序配置]
C --> C1[provider: dashscope]
C --> C2[apiKey: 独立API密钥]
C --> C3[baseUrl: 动态URL配置]
E --> E1[enabled: true/false]
E --> E2[modelName: qwen3-rerank]
E --> E3[topN: 5]
B --> B1[provider: deepseek]
B --> B2[apiKey: LLM专用密钥]
B --> B3[modelName: qwen-max]
end
subgraph "API Key 解析优先级"
P1[embedding.api-key] --> P2[最高优先级]
P3[model.api-key] --> P4[回退兼容]
P2 --> P5[直接使用]
P4 --> P6[fallback机制]
end
```

**图示来源**
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java:58-66](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java#L58-66)
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java:113-131](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java#L113-L131)
- [src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java:37-58](file://src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java#L37-L58)

### 智能 API Key 解析机制

**更新** EmbeddingService 和 RerankerService 都实现了智能的 API Key 解析逻辑，确保配置的灵活性和向后兼容性：

1. **优先级规则**：`embedding.api-key` > `model.api-key`（回退兼容）
2. **空值检查**：当 embedding.api-key 为空或空白时，自动回退到 model.api-key
3. **日志记录**：使用回退机制时会记录 INFO 级别日志，便于运维监控
4. **初始化日志**：启动时记录 provider、model、URL 等关键配置信息

### 配置示例

**更新** 配置文件中的 Embedding 和 Rerank 独立配置示例：

```yaml
agentscope:
  # LLM 模型配置（DeepSeek）
  model:
    provider: deepseek
    api-key: ${DEEPSEEK_API_KEY:sk-xxx}
    model-name: deepseek-chat
  
  # Embedding 独立配置（DashScope）
  embedding:
    api-key: ${EMBEDDING_API_KEY:${DASHSCOPE_API_KEY:}}
    provider: dashscope
    base-url: https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding
  
  # Rerank 独立配置（DashScope）
  rerank:
    enabled: true
    api-key: ${RERANK_API_KEY:${DASHSCOPE_API_KEY:}}
    model-name: qwen3-rerank
    base-url: https://dashscope.aliyuncs.com/compatible-api/v1/reranks
    top-n: 5
    score-threshold: 0.0
  
  # 知识库配置
  knowledge:
    embedding-model: text-embedding-v3
```

### 增强的日志记录

**更新** EmbeddingService 和 RerankerService 现在提供更详细的初始化日志：

- **Provider 信息**：记录使用的 Embedding Provider（默认 dashscope）
- **Model 信息**：记录嵌入模型名称（text-embedding-v3）
- **URL 信息**：记录 API Base URL 配置
- **回退日志**：当使用 model.api-key 作为回退时，记录明确的提示信息
- **Rerank 状态**：记录 Rerank 服务的启用状态、模型名称、超时配置等

**章节来源**
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java:58-66](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java#L58-66)
- [src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java:113-131](file://src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java#L113-L131)
- [src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java:37-58](file://src/main/java/com/tutorial/offerpilot/service/EmbeddingService.java#L37-L58)
- [src/main/java/com/tutorial/offerpilot/service/RerankerService.java:47-80](file://src/main/java/com/tutorial/offerpilot/service/RerankerService.java#L47-L80)
- [src/main/resources/application.yml:57-63](file://src/main/resources/application.yml#L57-63)
- [src/main/resources/application.yml:80-92](file://src/main/resources/application.yml#L80-92)