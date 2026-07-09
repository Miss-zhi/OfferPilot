# 多租户知识库设计

> **来源**：《03-详细设计说明书》§2  
> **模块**：多租户知识库  
> **前置依赖**：[rag-knowledge-base.md](rag-knowledge-base.md)

---

## 2.1 设计思路

不同用户属于不同行业，需要上传各自的面试题和经验。系统采用**公共库 + 私有库**两层架构：

```
┌─────────────────────────────────────────────────────────┐
│                    知识库可见性                            │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  PUBLIC（公共库）                                         │
│  ├── 面试题库（管理员创建，所有人可用）                     │
│  ├── 优秀答案库                                          │
│  ├── 公司面经库                                          │
│  ├── 学习资源库                                          │
│  ├── 简历模板库                                          │
│  ├── 薪资数据库                                          │
│  └── JD 模板库                                           │
│                                                          │
│  PRIVATE（私有库）                                        │
│  ├── 用户A: "金融风控面试题"                               │
│  ├── 用户A: "银行从业面经"                                │
│  ├── 用户B: "医疗AI面经"                                 │
│  └── 用户C: "游戏客户端面试题"                             │
│                                                          │
│  Agent 检索范围 = PUBLIC 库 + 当前用户的 PRIVATE 库        │
└─────────────────────────────────────────────────────────┘
```

## 2.2 数据库变更

`kb_knowledge_base` 表增加归属和可见性字段：

```sql
ALTER TABLE kb_knowledge_base ADD COLUMN owner_id VARCHAR(64);       -- 创建者 userId，ADMIN 创建公共库时为 NULL
ALTER TABLE kb_knowledge_base ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC';  -- PUBLIC / PRIVATE
```

## 2.3 检索逻辑

```java
@Service
public class KnowledgeBaseService {

    /**
     * 多租户检索：同时搜索公共库 + 用户私有库。
     *
     * @param userId 当前用户 ID，用于检索其私有知识库
     */
    public List<KnowledgeHit> searchWithTenant(String kbId, String query,
                                                int topK, String filter, String userId) {
        // 1. 确定检索范围：公共库 + 该用户的私有库
        List<String> collectionNames = new ArrayList<>();

        // 公共库
        List<KbKnowledgeBase> publicKbs = kbRepo.findByVisibility("PUBLIC");
        for (KbKnowledgeBase kb : publicKbs) {
            collectionNames.add(kb.getMilvusCollection());
        }

        // 用户私有库
        List<KbKnowledgeBase> privateKbs = kbRepo.findByOwnerAndVisibility(userId, "PRIVATE");
        for (KbKnowledgeBase kb : privateKbs) {
            collectionNames.add(kb.getMilvusCollection());
        }

        // 2. 对每个 Collection 执行检索，合并结果
        List<KnowledgeHit> allHits = new ArrayList<>();
        float[] queryEmbedding = embeddingService.embed(query);

        for (String collection : collectionNames) {
            SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withVectors(Collections.singletonList(queryEmbedding))
                .withTopK(topK)
                .withOutFields(List.of("doc_id", "chunk_index", "content", "tags", "metadata_json"))
                .withParams("{\"nprobe\": 16}")
                .build();

            SearchResult result = milvusClient.search(searchParam);
            allHits.addAll(parseSearchResult(result));
        }

        // 3. 按相似度分数排序，取 Top-K
        allHits.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return allHits.subList(0, Math.min(topK, allHits.size()));
    }

    /**
     * 创建知识库（区分公共/私有）。
     * ADMIN 创建公共库（owner_id = null, visibility = PUBLIC）
     * USER 创建私有库（owner_id = userId, visibility = PRIVATE）
     */
    @Transactional
    public KbKnowledgeBase createKnowledgeBase(String name, String description,
                                                String category, String userId, String role) {
        String kbId = generateKbId();
        String collectionName = "kb_" + kbId;

        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setKbId(kbId);
        kb.setName(name);
        kb.setDescription(description);
        kb.setCategory(category);
        kb.setMilvusCollection(collectionName);

        if ("ADMIN".equals(role)) {
            kb.setOwnerId(null);
            kb.setVisibility("PUBLIC");
        } else {
            kb.setOwnerId(userId);
            kb.setVisibility("PRIVATE");
        }

        kb.setStatus("ACTIVE");
        kbRepo.save(kb);

        // Milvus 创建 Collection（通用 Schema）
        createMilvusCollection(collectionName);

        return kb;
    }
}
```

## 2.4 工具层自动适配

Agent 的工具层无需改动。`QuestionSearchTool` 等检索工具调用 `KnowledgeBaseService.searchQuestions()` 时，Service 内部自动执行多租户检索。工具层传入的 `keyword` 会同时在公共题库和用户私有题库中检索，合并排序后返回。

```
Agent 调用 search_questions("银行风控")
  → QuestionSearchTool.searchQuestions()
    → KnowledgeBaseService.searchWithTenant(userId="user123")
      → 检索 PUBLIC 库: kb_interview_questions → 命中通用风控题
      → 检索 user123 的 PRIVATE 库: kb_金融风控面试题 → 命中行业专属题
      → 合并排序 → 返回 Top-5
```

## 2.5 管理权限

| 操作 | ADMIN | USER |
|:---|:---:|:---:|
| 创建知识库 | 创建公共库（所有人可见） | 创建私有库（仅自己可见） |
| 上传文档到公共库 | ✅ | ❌ |
| 上传文档到自己的私有库 | ✅ | ✅ |
| 删除公共库 | ✅ | ❌ |
| 删除自己的私有库 | ✅ | ✅ |
| 检索 | 公共库 + 自己的私有库 | 公共库 + 自己的私有库 |
