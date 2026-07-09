# RAG 知识库设计（向量存储 + 动态管理）

> **来源**：《03-详细设计说明书》§1  
> **模块**：RAG 知识库  
> **技术栈**：Milvus + DashScope Embedding + Spring Boot 3

---

## 1.1 向量存储技术选型

```
文档 → 分块(Chunking) → Embedding(DashScope) → 存入 Milvus
                                                    ↓
用户提问 → Query Embedding(DashScope) → Milvus 向量检索 → Rerank → 返回 Top-K
```

| 组件 | 选型 | 理由 |
|:---|:---|:---|
| 向量数据库 | **Milvus Standalone** | 开源、Docker 一键部署、Java SDK 成熟、支持混合检索（向量 + 标量过滤） |
| Embedding 模型 | **DashScope text-embedding-v3** | 和 LLM 同一家（阿里云），API Key 复用，1024 维，中文效果好 |
| Java SDK | **Milvus Java SDK 2.x** | 官方维护，API 稳定 |
| 备选方案 | Elasticsearch 8.x knn | 如果已有 ES 集群，不用额外部署 Milvus |

为什么选 Milvus 而不是 Elasticsearch：Milvus 是专用的向量数据库，向量检索性能更好，API 更简洁，学习成本低。ES 的向量检索是附加功能，配置复杂，适合"已有 ES 顺便用"的场景。

## 1.2 Milvus Collection 设计

采用**动态 Collection 创建**方案：所有知识库共用同一个通用 Schema，通过 `tags` 和 `metadata_json` 字段区分不同文档的元数据。前端点击"新建知识库"时，后端动态在 Milvus 创建对应的 Collection。

**通用 Collection Schema：**

```java
/**
 * 通用知识库 Collection Schema。
 * 所有知识库共用同一结构，通过 tags 和 metadata_json 区分。
 */
CreateCollectionParam schema = CreateCollectionParam.newBuilder()
    .withCollectionName("kb_" + kbId)
    .addFieldType(new FieldType(DataType.Int64, "id", true, true))       // 主键，自增
    .addFieldType(new FieldType(DataType.VarChar, "doc_id", 64))         // 所属文档 ID
    .addFieldType(new FieldType(DataType.Int32, "chunk_index", false))   // 分块序号
    .addFieldType(new FieldType(DataType.VarChar, "content", 8000))      // 分块文本内容
    .addFieldType(new FieldType(DataType.VarChar, "tags", 512))          // 标签
    .addFieldType(new FieldType(DataType.VarChar, "metadata_json", 4000)) // 自定义元数据 JSON
    .addFieldType(new FieldType(DataType.FloatVector, "embedding", 1024)) // 向量字段
    .build();
```

**索引配置：**

```java
// IVF_FLAT 索引：适合中小规模数据（< 100 万条），召回率高，内存占用低
Map<String, String> indexParams = Map.of(
    "metric_type", "COSINE",   // 余弦相似度
    "index_type", "IVF_FLAT",  // 倒排索引 + 扁平量化
    "nlist", "128"             // 聚类中心数
);
```

为什么选 IVF_FLAT 而不是 HNSW：HNSW 检索更快，但内存占用大（每个向量额外存图结构）。OfferPilot 的数据量不大（几千条），IVF_FLAT 足够快，内存更省，部署在普通开发机上没压力。

**初始 7 个知识库：**

| 知识库 | Collection 名 | 预估规模 | 数据来源 | 可见性 |
|:---|:---|:---|:---|:---|
| 面试题库 | kb_interview_questions | 1000+ 题 | JavaGuide、小林 coding、牛客公开面经 | PUBLIC |
| 优秀答案 | kb_excellent_answers | 200+ 篇 | 牛客公开面经、知乎高赞回答 | PUBLIC |
| 公司面经 | kb_company_interviews | 50+ 家 | 牛客讨论区公开帖子 | PUBLIC |
| 学习资源 | kb_learning_resources | 100+ 条 | 各技术社区公开教程 | PUBLIC |
| 简历模板 | kb_resume_templates | 20+ 份 | 超级简历、冷熊简历等公开模板 | PUBLIC |
| 薪资数据 | kb_salary_data | 30+ 条 | 牛客 offerShow 公开数据 | PUBLIC |
| JD 模板 | kb_jd_templates | 20+ 份 | Boss 直聘、拉勾网公开 JD | PUBLIC |

用户还可以创建自己的**私有知识库**（如"金融风控面试题"、"医疗行业面经"），可见性为 PRIVATE，仅自己可见。

## 1.3 知识库文档示例

**面试题库文档示例（Markdown 格式）：**

```
---
题目ID: JAVA-001
分类: Java基础 / 集合框架
难度: ★★★☆☆
题目: HashMap 的底层实现原理是什么？JDK 1.8 做了什么优化？
参考答案:
HashMap 基于数组+链表+红黑树（JDK1.8）。数组是主体，每个槽位
存储链表头节点。哈希冲突时，新元素以尾插法加入链表。当链表长度
超过 8 且数组长度 >= 64 时，链表转为红黑树（O(n)→O(logn)）。
JDK1.8 的优化：1) 尾插法替代头插法（解决并发扩容时的死链问题）；
2) 引入红黑树（解决大量哈希冲突时查询退化为 O(n)的问题）；
3) 扩容时使用高低位拆分，不需要重新计算 hash。
评分标准:
- 基础（60分）：说出数组+链表结构
- 良好（80分）：提到红黑树转换条件、尾插法
- 优秀（100分）：能讲出扩容机制、高低位拆分、并发问题
常见追问:
- ConcurrentHashMap 是怎么实现线程安全的？
- HashMap 的 hash 函数为什么用异或运算？
- 为什么红黑树转换阈值是 8？（泊松分布概率分析）
---
```

**优秀答案文档示例：**

```
---
类型: 行为面试
问题: 说一个你解决过的最有挑战的技术问题
评分维度: 问题描述清晰度、解决思路、技术深度、结果量化
优秀回答范例:
"在我上一个实习中（情境），负责的商品列表页加载时间超过 3 秒，
用户跳出率高达 40%（任务）。我做了三件事（行动）：
1）用 Chrome DevTools 分析发现首屏加载了 47 个接口，其中 12 个
是非关键路径的，我做了接口并行化和非关键接口延迟加载；
2）图片从 PNG 换成 WebP，配合懒加载，首屏图片体积从 2.3MB 降到
380KB；
3）对高频查询的商品数据加了 Redis 缓存，命中率 92%。
最终首屏加载时间从 3.2s 降到 0.8s，跳出率从 40% 降到 18%（结果）。"
点评: 这个回答好在——有具体数据（3秒→0.8秒、40%→18%）、
有清晰的技术决策过程、结果可量化。
---
```

**公司面经文档示例：**

```
---
公司: 字节跳动
岗位: Java后端开发
面试轮次: 3轮技术 + 1轮HR
高频考点:
- 算法：手撕 LRU 缓存、二叉树层序遍历、字符串编辑距离
- Java：HashMap 底层、JVM 内存模型、GC 算法、线程池参数
- Spring：IoC 原理、AOP 实现、Bean 生命周期、事务传播机制
- MySQL：索引数据结构、事务隔离级别、慢 SQL 优化
- Redis：数据结构、持久化策略、缓存穿透/击穿/雪崩
- 系统设计：设计短链系统、设计秒杀系统、设计消息推送系统
面试风格: 重基础原理，喜欢追问到底。算法要求手写，难度中等偏上。
面经来源: 牛客网 2025-2026 年公开帖子
---
```

## 1.4 文档分块策略（DocumentChunker）

支持 4 种分块策略，上传时可选择，也可在重建索引时更换：

| 策略 | 适用场景 | 原理 |
|:---|:---|:---|
| AUTO | 默认，自动检测 | 检查文档特征，自动选最合适的策略 |
| BY_QUESTION | 面试题、FAQ 等结构化文档 | 按 "---" 分隔符切分，一道题 = 一个 chunk |
| BY_HEADING | 长文档（技术文档、面经汇总） | 按 Markdown 标题（## / ###）切分 |
| BY_SIZE | 无结构长文本 | 固定大小滑动窗口，带重叠 |

```java
@Component
public class DocumentChunker {

    @Value("${agentscope.knowledge.chunk-size}")
    private int defaultChunkSize;    // 默认 500 字符

    @Value("${agentscope.knowledge.chunk-overlap}")
    private int defaultChunkOverlap; // 默认 50 字符

    /**
     * 根据策略分块。
     */
    public List<String> chunk(String text, String strategy) {
        return switch (strategy != null ? strategy : "AUTO") {
            case "BY_QUESTION" -> chunkByQuestion(text);
            case "BY_HEADING"  -> chunkByHeading(text);
            case "BY_SIZE"     -> chunkBySize(text, defaultChunkSize, defaultChunkOverlap);
            case "AUTO"        -> autoChunk(text);
            default            -> chunkBySize(text, defaultChunkSize, defaultChunkOverlap);
        };
    }

    /**
     * 自动检测：如果文档中有大量 "---" 分隔符，按题分块；
     * 如果有 Markdown 标题，按标题分块；否则按固定大小。
     */
    private List<String> autoChunk(String text) {
        long separatorCount = text.lines()
            .filter(line -> line.trim().equals("---"))
            .count();
        if (separatorCount >= 3) {
            return chunkByQuestion(text);
        }

        long headingCount = text.lines()
            .filter(line -> line.matches("^#{1,3}\\s+.*"))
            .count();
        if (headingCount >= 3) {
            return chunkByHeading(text);
        }

        return chunkBySize(text, defaultChunkSize, defaultChunkOverlap);
    }

    /** 按 "---" 分隔符分块（面试题格式） */
    private List<String> chunkByQuestion(String text) {
        return Arrays.stream(text.split("---"))
            .map(String::trim)
            .filter(s -> s.length() > 20)
            .collect(Collectors.toList());
    }

    /** 按 Markdown 标题分块 */
    private List<String> chunkByHeading(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.matches("^#{1,3}\\s+.*") && current.length() > 100) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (current.length() > 100) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    /** 按固定大小滑动窗口分块（兜底策略） */
    private List<String> chunkBySize(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
        }
        return chunks;
    }
}
```

## 1.5 Embedding 服务（DashScope）

```java
@Service
public class EmbeddingService {

    private final String apiKey;
    private final String modelName;  // text-embedding-v3
    private final OkHttpClient httpClient;

    public EmbeddingService(
            @Value("${agentscope.model.api-key}") String apiKey,
            @Value("${agentscope.knowledge.embedding-model}") String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 将文本转为向量。
     * DashScope text-embedding-v3 返回 1024 维浮点向量。
     */
    public float[] embed(String text) {
        String json = """
            {
                "model": "%s",
                "input": { "texts": ["%s"] },
                "parameters": { "text_type": "document" }
            }
            """.formatted(modelName, escapeJson(text));

        Request request = new Request.Builder()
            .url("https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(json, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            JsonNode root = new ObjectMapper().readTree(response.body().string());
            JsonNode embedding = root.path("output").path("embeddings")
                                      .get(0).path("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (IOException e) {
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量 Embedding（减少 API 调用次数，节省成本）。
     * DashScope 单次最多 25 条文本。
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += 25) {
            List<String> batch = texts.subList(i, Math.min(i + 25, texts.size()));
            results.addAll(embedBatchInternal(batch));
        }
        return results;
    }

    private List<float[]> embedBatchInternal(List<String> texts) {
        // 类似 embed()，但 input.texts 传多个
        // ... 省略具体实现，结构相同
    }
}
```

**成本估算：**
- 1000 道面试题，每题约 500 tokens → 50 万 tokens → 0.35 元（一次性入库）
- 每次检索 query 约 100 tokens → 0.00007 元/次
- 一天 100 次检索 → 0.007 元/天
- 结论：Embedding 成本极低，几乎可以忽略

## 1.6 异步入库管道（DocumentIngestionService）

用户上传文档后，后端异步执行 5 阶段入库管道，前端轮询查看进度：

```
上传文件 → PARSING → CHUNKING → EMBEDDING → INDEXING → ACTIVE
                     （任何环节出错 → FAILED）
```

```java
@Service
public class DocumentIngestionService {

    private final KnowledgeBaseRepository kbRepo;
    private final DocumentRepository docRepo;
    private final ChunkRepository chunkRepo;
    private final MilvusServiceClient milvusClient;
    private final EmbeddingService embeddingService;
    private final DocumentChunker chunker;
    private final DocumentParser parser;

    @Async("ingestionExecutor")
    public void ingestDocument(String docId) {
        KbDocument doc = docRepo.findByDocId(docId)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));

        try {
            // 阶段 1：解析文件，提取纯文本
            updateStatus(doc, "PARSING", 10, "正在解析文件...");
            String rawText = parser.parse(doc.getFilePath(), doc.getFileType());
            updateStatus(doc, "PARSING", 25, "解析完成，提取 " + rawText.length() + " 字符");

            // 阶段 2：文本分块
            updateStatus(doc, "CHUNKING", 35, "正在分块...");
            List<String> chunks = chunker.chunk(rawText, doc.getChunkStrategy());
            doc.setChunkCount(chunks.size());
            docRepo.save(doc);
            updateStatus(doc, "CHUNKING", 50, "分块完成，共 " + chunks.size() + " 块");

            // 阶段 3：批量 Embedding
            updateStatus(doc, "EMBEDDING", 55, "正在生成向量（" + chunks.size() + " 条）...");
            List<float[]> embeddings = embeddingService.embedBatch(chunks);
            updateStatus(doc, "EMBEDDING", 80, "向量生成完成");

            // 阶段 4：写入 Milvus
            updateStatus(doc, "INDEXING", 85, "正在写入向量数据库...");

            Map<String, String> metadata = parseMetadata(doc.getMetadataJson());
            List<JsonObject> rows = new ArrayList<>();
            List<KbChunk> chunkRecords = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                JsonObject row = new JsonObject();
                row.addProperty("doc_id", docId);
                row.addProperty("chunk_index", i);
                row.addProperty("content", chunks.get(i));
                row.addProperty("tags", doc.getTags() != null ? doc.getTags() : "");
                row.addProperty("metadata_json", doc.getMetadataJson() != null ? doc.getMetadataJson() : "{}");
                row.add("embedding", new Gson().toJsonTree(embeddings.get(i)));
                rows.add(row);

                KbChunk chunkRecord = new KbChunk();
                chunkRecord.setDocId(docId);
                chunkRecord.setKbId(doc.getKbId());
                chunkRecord.setChunkIndex(i);
                chunkRecord.setContent(chunks.get(i));
                chunkRecord.setContentHash(sha256(chunks.get(i)));
                chunkRecord.setTokenCount(estimateTokens(chunks.get(i)));
                chunkRecords.add(chunkRecord);
            }

            InsertResult insertResult = milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(getMilvusCollection(doc.getKbId()))
                .withRows(rows)
                .build());

            List<Long> milvusIds = insertResult.getInsertIds().get(0);
            for (int i = 0; i < chunkRecords.size(); i++) {
                chunkRecords.get(i).setMilvusOffset(milvusIds.get(i));
            }
            chunkRepo.saveAll(chunkRecords);

            // 阶段 5：完成
            updateStatus(doc, "ACTIVE", 100, "入库完成");
            doc.setIndexedAt(Instant.now());
            docRepo.save(doc);
            updateKbStats(doc.getKbId());

        } catch (Exception e) {
            log.error("文档入库失败: docId={}", docId, e);
            updateStatus(doc, "FAILED", doc.getProgress(), "入库失败: " + e.getMessage());
        }
    }

    /** 删除文档：同时清理 MySQL 和 Milvus */
    @Transactional
    public void deleteDocument(String docId) {
        KbDocument doc = docRepo.findByDocId(docId)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));

        milvusClient.delete(DeleteParam.newBuilder()
            .withCollectionName(getMilvusCollection(doc.getKbId()))
            .withExpr("doc_id == \"" + docId + "\"")
            .build());

        chunkRepo.deleteByDocId(docId);
        docRepo.delete(doc);
        updateKbStats(doc.getKbId());
    }

    /** 重建索引：重新分块 + Embedding + 写入 */
    @Async("ingestionExecutor")
    public void reindexDocument(String docId) {
        KbDocument doc = docRepo.findByDocId(docId)
            .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));

        milvusClient.delete(DeleteParam.newBuilder()
            .withCollectionName(getMilvusCollection(doc.getKbId()))
            .withExpr("doc_id == \"" + docId + "\"")
            .build());
        chunkRepo.deleteByDocId(docId);
        ingestDocument(docId);
    }
}
```

**DocumentParser（多格式解析）：**

```java
@Component
public class DocumentParser {

    public String parse(String filePath, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "markdown", "md" -> parseMarkdown(filePath);
            case "pdf"            -> parsePdf(filePath);
            case "txt"            -> Files.readString(Path.of(filePath));
            case "docx"           -> parseDocx(filePath);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + fileType);
        };
    }

    private String parseMarkdown(String filePath) throws IOException {
        String content = Files.readString(Path.of(filePath));
        return content
            .replaceAll("#{1,6}\\s+", "")
            .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
            .replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1")
            .replaceAll("```[\\s\\S]*?```", "")
            .trim();
    }

    private String parsePdf(String filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            return new PDFTextStripper().getText(document).trim();
        }
    }

    private String parseDocx(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            document.getTables().forEach(t -> {
                t.getRows().forEach(r -> {
                    r.getTableCells().forEach(c -> sb.append(c.getText()).append("\t"));
                    sb.append("\n");
                });
            });
            return sb.toString().trim();
        }
    }
}
```

## 1.7 检索策略

采用"混合检索"——多路召回后合并：

```
用户问："字节跳动 Java 后端面试考什么"

→ 第1路：向量检索（语义匹配）
  query = "字节跳动 Java 后端面试高频考点"
  query embedding → Milvus 余弦相似度检索
  命中：bytedance 面经文档（相似度 0.94）

→ 第2路：标量过滤（精确条件）
  tags like "%字节跳动%" AND tags like "%Java后端%"
  命中：同上但更精准

→ 第3路：关联检索（扩展信息）
  从面经中提取的高频考点关键词 → 去题库 Collection 检索具体题目
  命中：JAVA-001, MYSQL-015, REDIS-008...

→ 合并 + 去重 + 按相关性排序 → 返回给 Agent
```
