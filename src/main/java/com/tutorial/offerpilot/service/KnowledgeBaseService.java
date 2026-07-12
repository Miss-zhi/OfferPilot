/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.config.AgentScopeProperties;
import com.tutorial.offerpilot.converter.KbConverter;
import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.DocDetailResponse;
import com.tutorial.offerpilot.dto.kb.DocProgress;
import com.tutorial.offerpilot.dto.kb.DocResponse;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.dto.kb.KbStatsResponse;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse.SearchHit;
import com.tutorial.offerpilot.dto.tool.AnswerSearchResult;
import com.tutorial.offerpilot.dto.tool.CompanySearchResult;
import com.tutorial.offerpilot.dto.tool.QuestionSearchResult;
import com.tutorial.offerpilot.dto.tool.ResourceListResult;
import com.tutorial.offerpilot.dto.tool.SearchRequest;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.KbChunk;
import com.tutorial.offerpilot.entity.KbDocument;
import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.ResourceNotFoundException;
import com.tutorial.offerpilot.repository.ChunkRepository;
import com.tutorial.offerpilot.repository.DocumentRepository;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.KnowledgeBaseRepository;
import com.tutorial.offerpilot.service.ingestion.DocumentIngestionService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepo;
    private final DocumentRepository docRepo;
    private final ChunkRepository chunkRepo;
    private final MilvusClientV2 milvusClient;
    private final KbConverter kbConverter;
    private final VectorSearchService vectorSearchService;
    private final InterviewQuestionRepository questionRepo;
    private final FileService fileService;
    private final DocumentIngestionService ingestionService;
    private final SearchAnalyticsService searchAnalyticsService;
    private final MilvusCollectionManager milvusCollectionManager;
    private final ApplicationEventPublisher eventPublisher;
    private final RerankerService rerankerService;
    private final Executor searchExecutor;
    private final AgentScopeProperties agentScopeProperties;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                DocumentRepository docRepo,
                                ChunkRepository chunkRepo,
                                MilvusClientV2 milvusClient,
                                KbConverter kbConverter,
                                VectorSearchService vectorSearchService,
                                InterviewQuestionRepository questionRepo,
                                FileService fileService,
                                DocumentIngestionService ingestionService,
                                SearchAnalyticsService searchAnalyticsService,
                                MilvusCollectionManager milvusCollectionManager,
                                ApplicationEventPublisher eventPublisher,
                                RerankerService rerankerService,
                                @Qualifier("searchExecutor") Executor searchExecutor,
                                AgentScopeProperties agentScopeProperties) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.milvusClient = milvusClient;
        this.kbConverter = kbConverter;
        this.vectorSearchService = vectorSearchService;
        this.questionRepo = questionRepo;
        this.fileService = fileService;
        this.ingestionService = ingestionService;
        this.searchAnalyticsService = searchAnalyticsService;
        this.milvusCollectionManager = milvusCollectionManager;
        this.eventPublisher = eventPublisher;
        this.rerankerService = rerankerService;
        this.searchExecutor = searchExecutor;
        this.agentScopeProperties = agentScopeProperties;
    }

    @Transactional
    public KbResponse createKnowledgeBase(CreateKbRequest req, String userId, UserDetails currentUser) {
        String kbId = "kb-" + UUID.randomUUID().toString().substring(0, 8);
        // Milvus collection name 只允许字母、数字、下划线，将 kbId 中的连字符替换为下划线
        String collectionName = "kb_" + kbId.replace('-', '_');
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setKbId(kbId);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setCategory(req.getCategory());
        kb.setMilvusCollection(collectionName);
        kb.setStatus("ACTIVE");

        if (isAdmin) {
            kb.setOwnerId(null);
            kb.setVisibility("PUBLIC");
        } else {
            kb.setOwnerId(userId);
            kb.setVisibility("PRIVATE");
        }

        kbRepo.save(kb);

        // 同步创建 Milvus Collection（含索引并加载到内存）
        milvusCollectionManager.createCollection(collectionName);

        log.info("Knowledge base created: kbId={}, visibility={}", kbId, kb.getVisibility());
        return kbConverter.toResponse(kb);
    }

    public List<KbResponse> listKnowledgeBases(String userId, UserDetails currentUser) {
        List<KbKnowledgeBase> kbs = kbRepo.findPublicOrOwnedBy(userId);
        return kbs.stream().map(kbConverter::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public void deleteKnowledgeBase(String kbId, String userId, UserDetails currentUser) {
        KbKnowledgeBase kb = kbRepo.findByKbId(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("知识库不存在: " + kbId));

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && (kb.getOwnerId() == null || !kb.getOwnerId().equals(userId))) {
            throw new BusinessException(403, "无权删除此知识库");
        }

        String collectionName = kb.getMilvusCollection();

        // 1. 级联删除 Milvus Collection
        if (collectionName != null && !collectionName.isBlank()) {
            try {
                milvusClient.dropCollection(DropCollectionReq.builder()
                        .collectionName(collectionName)
                        .build());
                log.info("Milvus collection dropped: {}", collectionName);
            } catch (Exception e) {
                log.warn("Failed to drop Milvus collection {}: {}", collectionName, e.getMessage());
            }
        }

        // 2. 级联删除分块
        long chunkCount = chunkRepo.countByKbId(kbId);
        chunkRepo.deleteByKbId(kbId);
        log.info("Deleted {} chunks for kbId={}", chunkCount, kbId);

        // 3. 级联删除文档
        long docCount = docRepo.countByKbId(kbId);
        docRepo.deleteByKbId(kbId);
        log.info("Deleted {} documents for kbId={}", docCount, kbId);

        // 4. 删除知识库
        kbRepo.delete(kb);
        log.info("Knowledge base deleted: kbId={}, collection={}, chunks={}, docs={}",
                kbId, collectionName, chunkCount, docCount);
    }

    // ======================== 以下是新增的搜索方法 ========================

    /**
     * 搜索面试题库（简化接口，向后兼容）。
     * 仅搜索 PUBLIC KB，不包含用户私有 KB。
     */
    @Cacheable(value = "searchQuestions", key = "#keyword")
    public QuestionSearchResult searchQuestions(String keyword) {
        SearchRequest req = new SearchRequest();
        req.setKeywords(keyword);
        req.setTopK(10);
        return searchQuestions(req);
    }

    /**
     * 搜索面试题库（结构化参数）。
     * 多路并行召回：Milvus 向量检索（PUBLIC + 用户 PRIVATE KBs） + MySQL LIKE → RRF 融合 → Rerank 精排。
     */
    @Cacheable(value = "searchQuestions", key = "#req.keywords + '_' + #req.category + '_' + #req.difficulty + '_' + #req.position + '_' + #req.userId")
    public QuestionSearchResult searchQuestions(SearchRequest req) {
        String keyword = req.getKeywords();
        String userId = req.getUserId();
        String filterExpr = req.buildFilterExpr();
        int recallTopK = 20; // 召回阶段多取一些候选
        int dbLimit = 10;
        long start = System.currentTimeMillis();

        // Phase 1: 并行召回
        CompletableFuture<List<SearchHit>> futureA = CompletableFuture.supplyAsync(
                () -> milvusRecall(keyword, recallTopK, filterExpr, userId), searchExecutor);
        CompletableFuture<List<SearchHit>> futureB = CompletableFuture.supplyAsync(
                () -> mysqlRecallQuestions(keyword, dbLimit), searchExecutor);

        List<SearchHit> pathA = getQuietly(futureA, "Milvus");
        List<SearchHit> pathB = getQuietly(futureB, "MySQL");

        // Phase 2: RRF 融合
        long fusionStart = System.currentTimeMillis();
        List<SearchHit> fused = VectorSearchService.fuseWithRRF(List.of(pathA, pathB));
        long fusionMs = System.currentTimeMillis() - fusionStart;

        // Phase 3: Rerank 精排
        List<QuestionSearchResult.QuestionItem> items = rerankAndBuildQuestions(keyword, fused);

        // 统计
        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = (int) items.stream().filter(i -> "db".equals(i.getSource())).count();
        int webHits = 0;

        long latencyMs = System.currentTimeMillis() - start;
        searchAnalyticsService.logSearch(userId, keyword, "searchQuestions",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchQuestions: keyword={}, userId={}, milvusHits={}, dbHits={}, fused={}, results={}, fusionMs={}, latencyMs={}",
                keyword, userId, pathA.size(), pathB.size(), fused.size(), items.size(), fusionMs, latencyMs);
        return new QuestionSearchResult(items.size(), items);
    }

    /**
     * 搜索优秀面试答案库（简化接口，向后兼容）。
     * 仅搜索 PUBLIC KB，不包含用户私有 KB。
     */
    @Cacheable(value = "searchAnswers", key = "#keyword")
    public AnswerSearchResult searchAnswers(String keyword) {
        SearchRequest req = new SearchRequest();
        req.setKeywords(keyword);
        req.setTopK(10);
        return searchAnswers(req);
    }

    /**
     * 搜索优秀面试答案库（结构化参数）。
     * 多路并行召回：Milvus 向量检索（PUBLIC + 用户 PRIVATE KBs） + MySQL LIKE（仅含有答案的题目）→ RRF 融合 → Rerank 精排。
     */
    @Cacheable(value = "searchAnswers", key = "#req.keywords + '_' + #req.category + '_' + #req.difficulty + '_' + #req.position + '_' + #req.userId")
    public AnswerSearchResult searchAnswers(SearchRequest req) {
        String keyword = req.getKeywords();
        String userId = req.getUserId();
        String filterExpr = req.buildFilterExpr();
        int recallTopK = 20;
        int dbLimit = 10;
        long start = System.currentTimeMillis();

        // Phase 1: 并行召回
        CompletableFuture<List<SearchHit>> futureA = CompletableFuture.supplyAsync(
                () -> milvusRecall(keyword, recallTopK, filterExpr, userId), searchExecutor);
        CompletableFuture<List<SearchHit>> futureB = CompletableFuture.supplyAsync(
                () -> mysqlRecallAnswers(keyword, dbLimit), searchExecutor);

        List<SearchHit> pathA = getQuietly(futureA, "Milvus");
        List<SearchHit> pathB = getQuietly(futureB, "MySQL");

        // Phase 2: RRF 融合
        List<SearchHit> fused = VectorSearchService.fuseWithRRF(List.of(pathA, pathB));

        // Phase 3: Rerank 精排
        List<AnswerSearchResult.AnswerItem> items = rerankAndBuildAnswers(keyword, fused);

        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = (int) items.stream().filter(i -> "db".equals(i.getSource())).count();
        int webHits = 0;

        long latencyMs = System.currentTimeMillis() - start;
        searchAnalyticsService.logSearch(userId, keyword, "searchAnswers",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchAnswers: keyword={}, userId={}, milvusHits={}, dbHits={}, results={}, latencyMs={}",
                keyword, userId, pathA.size(), pathB.size(), items.size(), latencyMs);
        return new AnswerSearchResult(items.size(), items);
    }

    /**
     * 搜索公司面试经验（简化接口，向后兼容）。
     * 仅搜索 PUBLIC KB，不包含用户私有 KB。
     */
    @Cacheable(value = "searchCompanyInterviews", key = "#companyName")
    public CompanySearchResult searchCompanyInterviews(String companyName) {
        SearchRequest req = new SearchRequest();
        req.setKeywords(companyName);
        req.setCompany(companyName);
        req.setTopK(10);
        return searchCompanyInterviews(req);
    }

    /**
     * 搜索公司面试经验（结构化参数）。
     * 多路并行召回：Milvus 向量检索（PUBLIC + 用户 PRIVATE KBs，优先匹配公司名称）+ MySQL LIKE → RRF 融合 → Rerank 精排。
     */
    @Cacheable(value = "searchCompanyInterviews", key = "#req.company + '_' + #req.keywords + '_' + #req.category + '_' + #req.difficulty + '_' + #req.userId")
    public CompanySearchResult searchCompanyInterviews(SearchRequest req) {
        String companyName = req.getCompany() != null ? req.getCompany() : req.getKeywords();
        String userId = req.getUserId();
        String filterExpr = req.buildFilterExpr();
        int recallTopK = 20;
        int dbLimit = 10;
        long start = System.currentTimeMillis();

        // Phase 1: 并行召回
        CompletableFuture<List<SearchHit>> futureA = CompletableFuture.supplyAsync(
                () -> milvusRecallCompany(companyName, recallTopK, filterExpr, userId), searchExecutor);
        CompletableFuture<List<SearchHit>> futureB = CompletableFuture.supplyAsync(
                () -> mysqlRecallByKeyword(companyName, dbLimit), searchExecutor);

        List<SearchHit> pathA = getQuietly(futureA, "Milvus");
        List<SearchHit> pathB = getQuietly(futureB, "MySQL");

        // Phase 2: RRF 融合
        List<SearchHit> fused = VectorSearchService.fuseWithRRF(List.of(pathA, pathB));

        // Phase 3: Rerank 精排
        List<CompanySearchResult.CompanyItem> items = rerankAndBuildCompanies(companyName, fused);

        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = (int) items.stream().filter(i -> "db".equals(i.getSource())).count();
        int webHits = 0;

        long latencyMs = System.currentTimeMillis() - start;
        searchAnalyticsService.logSearch(userId, companyName, "searchCompanyInterviews",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchCompanyInterviews: company={}, userId={}, milvusHits={}, dbHits={}, results={}, latencyMs={}",
                companyName, userId, pathA.size(), pathB.size(), items.size(), latencyMs);
        return new CompanySearchResult(items.size(), items);
    }

    /**
     * 搜索学习资源（简化接口，向后兼容）。
     * 仅搜索 PUBLIC KB，不包含用户私有 KB。
     */
    @Cacheable(value = "searchResources", key = "#topic")
    public ResourceListResult searchResources(String topic) {
        SearchRequest req = new SearchRequest();
        req.setKeywords(topic);
        req.setTopK(10);
        return searchResources(req);
    }

    /**
     * 搜索学习资源（结构化参数）。
     * 资源类数据主要来自知识库文档，无 MySQL 路径，直接走 Rerank 精排。
     * 搜索范围：PUBLIC + 用户 PRIVATE KBs。
     */
    @Cacheable(value = "searchResources", key = "#req.keywords + '_' + #req.category + '_' + #req.difficulty + '_' + #req.userId")
    public ResourceListResult searchResources(SearchRequest req) {
        String topic = req.getKeywords();
        String userId = req.getUserId();
        String filterExpr = req.buildFilterExpr();
        int recallTopK = 20;
        long start = System.currentTimeMillis();

        // Path: Milvus 向量检索
        List<SearchHit> hits = milvusRecall(topic, recallTopK, filterExpr, userId);

        // Rerank 精排（单路召回跳过 RRF，直接 Rerank）
        List<ResourceListResult.ResourceItem> items = rerankAndBuildResources(topic, hits);

        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = 0;
        int webHits = 0;

        long latencyMs = System.currentTimeMillis() - start;
        searchAnalyticsService.logSearch(userId, topic, "searchResources",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchResources: topic={}, userId={}, milvusHits={}, results={}, latencyMs={}",
                topic, userId, hits.size(), items.size(), latencyMs);
        return new ResourceListResult(items.size(), items);
    }

    // ======================== 召回辅助方法 ========================

    /**
     * 获取 CompletableFuture 结果，异常/超时时取消另一路并返回空列表。
     */
    private List<SearchHit> getQuietly(CompletableFuture<List<SearchHit>> future, String pathName) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("{} recall failed: {}", pathName, e.getMessage());
            future.cancel(true);
            return List.of();
        }
    }

    /**
     * Milvus 通用召回：收集 PUBLIC KB + 用户 PRIVATE KB 的 Collection，执行多 Collection 检索。
     *
     * @param userId 用户 ID，为 null 时仅搜索 PUBLIC KB
     */
    private List<SearchHit> milvusRecall(String query, int topK, String filterExpr, String userId) {
        List<KbKnowledgeBase> kbs;
        if (userId != null && !userId.isBlank()) {
            kbs = kbRepo.findPublicOrOwnedBy(userId);
        } else {
            kbs = kbRepo.findByVisibility("PUBLIC");
        }
        List<String> collections = new ArrayList<>();
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                collections.add(kb.getMilvusCollection());
            }
        }
        if (collections.isEmpty()) {
            return List.of();
        }
        try {
            List<SearchHit> hits = vectorSearchService.searchMultiCollection(collections, query, topK, topK * 2, filterExpr);
            // 标记来源
            for (SearchHit hit : hits) {
                hit.setSource("kb");
            }
            return hits;
        } catch (Exception e) {
            log.warn("Milvus recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Milvus 公司面试召回：优先匹配公司名称的 KB Collection（PUBLIC + 用户 PRIVATE），无匹配则全量检索。
     *
     * @param userId 用户 ID，为 null 时仅搜索 PUBLIC KB
     */
    private List<SearchHit> milvusRecallCompany(String companyName, int topK, String filterExpr, String userId) {
        List<KbKnowledgeBase> kbs;
        if (userId != null && !userId.isBlank()) {
            kbs = kbRepo.findPublicOrOwnedBy(userId);
        } else {
            kbs = kbRepo.findByVisibility("PUBLIC");
        }
        List<String> collections = new ArrayList<>();
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                if (kb.getName() != null && kb.getName().toLowerCase().contains(companyName.toLowerCase())) {
                    collections.add(kb.getMilvusCollection());
                }
            }
        }
        // 没有精确匹配则全量
        if (collections.isEmpty()) {
            for (KbKnowledgeBase kb : kbs) {
                if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                    collections.add(kb.getMilvusCollection());
                }
            }
        }
        if (collections.isEmpty()) {
            return List.of();
        }
        try {
            List<SearchHit> hits = vectorSearchService.searchMultiCollection(
                    collections, companyName + " 面试", topK, topK * 2, filterExpr);
            // 标记来源
            for (SearchHit hit : hits) {
                hit.setSource("kb");
            }
            return hits;
        } catch (Exception e) {
            log.warn("Milvus company recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * MySQL LIKE 检索面试题，返回 SearchHit 列表。
     */
    private List<SearchHit> mysqlRecallQuestions(String keyword, int limit) {
        try {
            List<InterviewQuestion> questions = questionRepo.searchByKeyword(
                    keyword, PageRequest.of(0, limit));
            List<SearchHit> hits = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                InterviewQuestion q = questions.get(i);
                SearchHit hit = new SearchHit();
                hit.setDocId(q.getQuestionId());
                hit.setChunkIndex(0);
                hit.setContent(q.getQuestionText());
                hit.setScore(0.8f - i * 0.02f);
                hit.setSource("db");
                hits.add(hit);
            }
            return hits;
        } catch (Exception e) {
            log.warn("MySQL recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * MySQL LIKE 检索含答案的面试题。
     */
    private List<SearchHit> mysqlRecallAnswers(String keyword, int limit) {
        try {
            List<InterviewQuestion> questions = questionRepo.searchWithAnswerByKeyword(
                    keyword, PageRequest.of(0, limit));
            List<SearchHit> hits = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                InterviewQuestion q = questions.get(i);
                SearchHit hit = new SearchHit();
                hit.setDocId(q.getQuestionId());
                hit.setChunkIndex(0);
                hit.setContent(q.getQuestionText());
                hit.setScore(0.8f - i * 0.02f);
                hit.setSource("db");
                hits.add(hit);
            }
            return hits;
        } catch (Exception e) {
            log.warn("MySQL answer recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * MySQL LIKE 通用关键词检索。
     */
    private List<SearchHit> mysqlRecallByKeyword(String keyword, int limit) {
        return mysqlRecallQuestions(keyword, limit);
    }

    // ======================== Rerank + 构建输出 ========================

    /**
     * Rerank 上下文：包含原始候选列表和精排结果，用于映射回原始 SearchHit 元数据。
     */
    private record RerankContext(List<SearchHit> candidates, List<RerankerService.RerankResult> results) {}

    /**
     * 通用 Rerank 辅助：从 fused 结果中提取文档内容，调用 Reranker 精排。
     * 返回 RerankContext 以保留原始 SearchHit 元数据（docId、source 等）。
     */
    private RerankContext doRerank(String query, List<SearchHit> fused) {
        if (fused.isEmpty()) {
            return new RerankContext(List.of(), List.of());
        }
        List<SearchHit> top20 = fused.size() > 20 ? fused.subList(0, 20) : fused;
        List<String> docs = top20.stream().map(SearchHit::getContent).toList();
        int topN = agentScopeProperties.getRerank().getTopN();
        List<RerankerService.RerankResult> rawResults = rerankerService.rerank(query, docs, topN);
        // 过滤越界 index，防御 Rerank API 异常返回
        List<RerankerService.RerankResult> safeResults = new ArrayList<>();
        for (RerankerService.RerankResult rr : rawResults) {
            if (rr.getIndex() >= 0 && rr.getIndex() < top20.size()) {
                safeResults.add(rr);
            } else {
                log.warn("Rerank returned out-of-bounds index: {}, candidates size: {}", rr.getIndex(), top20.size());
            }
        }
        return new RerankContext(top20, safeResults);
    }

    private List<QuestionSearchResult.QuestionItem> rerankAndBuildQuestions(
            String keyword, List<SearchHit> fused) {
        RerankContext ctx = doRerank(keyword, fused);
        List<QuestionSearchResult.QuestionItem> items = new ArrayList<>();
        for (RerankerService.RerankResult rr : ctx.results()) {
            SearchHit hit = ctx.candidates().get(rr.getIndex());
            QuestionSearchResult.QuestionItem item = new QuestionSearchResult.QuestionItem();
            item.setQuestionId(hit.getDocId() != null ? hit.getDocId() : rr.getDocument());
            item.setContent(truncate(hit.getContent(), 200));
            item.setCategory("通用");
            item.setRelevanceScore((float) rr.getRelevanceScore());
            item.setSource(hit.getSource() != null ? hit.getSource() : "kb");
            items.add(item);
        }
        return items;
    }

    private List<AnswerSearchResult.AnswerItem> rerankAndBuildAnswers(
            String keyword, List<SearchHit> fused) {
        RerankContext ctx = doRerank(keyword, fused);
        List<AnswerSearchResult.AnswerItem> items = new ArrayList<>();
        for (RerankerService.RerankResult rr : ctx.results()) {
            SearchHit hit = ctx.candidates().get(rr.getIndex());
            AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
            item.setAnswerId(hit.getDocId() != null ? hit.getDocId() : rr.getDocument());
            item.setQuestion(keyword);
            item.setAnswer(truncate(hit.getContent(), 300));
            item.setCategory("通用");
            item.setRelevanceScore((float) rr.getRelevanceScore());
            item.setSource(hit.getSource() != null ? hit.getSource() : "kb");
            items.add(item);
        }
        return items;
    }

    private List<CompanySearchResult.CompanyItem> rerankAndBuildCompanies(
            String companyName, List<SearchHit> fused) {
        RerankContext ctx = doRerank(companyName, fused);
        List<CompanySearchResult.CompanyItem> items = new ArrayList<>();
        for (RerankerService.RerankResult rr : ctx.results()) {
            SearchHit hit = ctx.candidates().get(rr.getIndex());
            CompanySearchResult.CompanyItem item = new CompanySearchResult.CompanyItem();
            item.setCompanyName(companyName);
            item.setInterviewType(inferInterviewType(hit.getContent()));
            item.setSummary(truncate(hit.getContent(), 200));
            item.setDifficulty("中等");
            item.setRelevanceScore((float) rr.getRelevanceScore());
            item.setSource(hit.getSource() != null ? hit.getSource() : "kb");
            items.add(item);
        }
        return items;
    }

    private List<ResourceListResult.ResourceItem> rerankAndBuildResources(
            String topic, List<SearchHit> hits) {
        RerankContext ctx = doRerank(topic, hits);
        List<ResourceListResult.ResourceItem> items = new ArrayList<>();
        for (RerankerService.RerankResult rr : ctx.results()) {
            SearchHit hit = ctx.candidates().get(rr.getIndex());
            ResourceListResult.ResourceItem item = new ResourceListResult.ResourceItem();
            item.setTitle(truncate(hit.getContent(), 80));
            item.setUrl("");
            item.setType("文档");
            item.setRelevanceScore((float) rr.getRelevanceScore());
            item.setSource(hit.getSource() != null ? hit.getSource() : "kb");
            items.add(item);
        }
        return items;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String inferInterviewType(String content) {
        if (content == null) {
            return "综合面试";
        }
        String lower = content.toLowerCase();
        if (lower.contains("技术") || lower.contains("算法") || lower.contains("代码")) {
            return "技术面试";
        }
        if (lower.contains("hr") || lower.contains("行为") || lower.contains("沟通")) {
            return "HR/行为面试";
        }
        if (lower.contains("系统设计") || lower.contains("架构")) {
            return "系统设计面试";
        }
        return "综合面试";
    }

    // ======================== 以下是新增的 KB 文档管理方法 ========================

    /**
     * 验证知识库访问权限：存在性 + 所有权或管理员检查。
     */
    private KbKnowledgeBase validateKbAccess(String kbId, String userId, UserDetails currentUser) {
        KbKnowledgeBase kb = kbRepo.findByKbId(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("知识库不存在: " + kbId));

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && (kb.getOwnerId() == null || !kb.getOwnerId().equals(userId))) {
            throw new BusinessException(403, "无权访问此知识库");
        }
        return kb;
    }

    /**
     * 获取知识库统计信息。
     */
    public KbStatsResponse getKbStats(String kbId, String userId, UserDetails currentUser) {
        KbKnowledgeBase kb = validateKbAccess(kbId, userId, currentUser);

        long activeCount = docRepo.countByKbIdAndStatus(kbId, "ACTIVE");
        long failedCount = docRepo.countByKbIdAndStatus(kbId, "FAILED");

        KbStatsResponse resp = new KbStatsResponse();
        resp.setKbId(kb.getKbId());
        resp.setName(kb.getName());
        resp.setDocumentCount(kb.getDocumentCount());
        resp.setChunkCount(kb.getChunkCount());
        resp.setActiveDocuments(activeCount);
        resp.setFailedDocuments(failedCount);
        return resp;
    }

    /**
     * 查询知识库下的文档列表。
     */
    public List<DocResponse> listDocs(String kbId, String userId, UserDetails currentUser) {
        validateKbAccess(kbId, userId, currentUser);

        List<KbDocument> docs = docRepo.findByKbId(kbId);
        return docs.stream().map(kbConverter::toDocResponse).collect(Collectors.toList());
    }

    /**
     * 查询文档详情，包含分块预览列表。
     */
    public DocDetailResponse getDocDetail(String kbId, String docId, String userId, UserDetails currentUser) {
        validateKbAccess(kbId, userId, currentUser);

        KbDocument doc = docRepo.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在: " + docId));

        if (!doc.getKbId().equals(kbId)) {
            throw new BusinessException(400, "文档不属于该知识库");
        }

        List<KbChunk> chunks = chunkRepo.findByDocIdOrderByChunkIndex(docId);
        // 限制分块预览数量，避免响应过大
        if (chunks.size() > 50) {
            chunks = chunks.subList(0, 50);
        }

        return kbConverter.toDocDetailResponse(doc, chunks);
    }

    /**
     * 创建文档记录（由 Controller 上传后调用）。
     */
    @CacheEvict(value = {"searchQuestions", "searchAnswers", "searchCompanyInterviews", "searchResources"}, allEntries = true)
    @Transactional
    public DocResponse createDoc(String kbId, String fileName, String filePath, String fileType,
                                  Long fileSize, String chunkStrategy, String userId, UserDetails currentUser) {
        validateKbAccess(kbId, userId, currentUser);

        String docId = "doc-" + UUID.randomUUID().toString().substring(0, 8);

        KbDocument doc = new KbDocument();
        doc.setDocId(docId);
        doc.setKbId(kbId);
        doc.setFileName(fileName);
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize(fileSize);
        doc.setChunkStrategy(chunkStrategy != null ? chunkStrategy : "AUTO");
        doc.setStatus("UPLOADED");
        doc.setProgress(0);
        doc.setChunkCount(0);
        doc.setUploadedAt(Instant.now());
        docRepo.save(doc);

        log.info("Document created: docId={}, kbId={}, fileName={}", docId, kbId, fileName);

        // 事务提交后通过事件触发异步入库，确保数据已持久化
        eventPublisher.publishEvent(new DocumentIngestionEvent(this, docId));

        return kbConverter.toDocResponse(doc);
    }

    /**
     * 删除文档及其关联分块，更新知识库统计。
     */
    @CacheEvict(value = {"searchQuestions", "searchAnswers", "searchCompanyInterviews", "searchResources"}, allEntries = true)
    @Transactional
    public void deleteDoc(String kbId, String docId, String userId, UserDetails currentUser) {
        KbKnowledgeBase kb = validateKbAccess(kbId, userId, currentUser);

        KbDocument doc = docRepo.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在: " + docId));

        if (!doc.getKbId().equals(kbId)) {
            throw new BusinessException(400, "文档不属于该知识库");
        }

        String collectionName = kb.getMilvusCollection();

        // 1. 从 Milvus 删除向量
        if (collectionName != null && !collectionName.isBlank()) {
            try {
                milvusClient.delete(DeleteReq.builder()
                        .collectionName(collectionName)
                        .filter("doc_id == '" + docId + "'")
                        .build());
                log.info("Deleted vectors from Milvus: collection={}, docId={}", collectionName, docId);
            } catch (Exception e) {
                log.warn("Failed to delete vectors from Milvus {} for docId={}: {}",
                        collectionName, docId, e.getMessage());
            }
        }

        // 2. 删除 DB 分块
        long chunkCount = chunkRepo.countByDocId(docId);
        chunkRepo.deleteByDocId(docId);

        // 3. 删除 DB 文档
        docRepo.delete(doc);

        // 4. 更新知识库统计
        kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
        kb.setChunkCount(Math.max(0, (int) (kb.getChunkCount() - chunkCount)));
        kbRepo.save(kb);

        log.info("Document deleted: docId={}, kbId={}, chunks={}", docId, kbId, chunkCount);
    }

    /**
     * 获取文档入库进度。
     */
    public DocProgress getDocProgress(String kbId, String docId, String userId, UserDetails currentUser) {
        validateKbAccess(kbId, userId, currentUser);

        KbDocument doc = docRepo.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在: " + docId));

        if (!doc.getKbId().equals(kbId)) {
            throw new BusinessException(400, "文档不属于该知识库");
        }

        return new DocProgress(doc.getStatus(), doc.getProgress());
    }

    /**
     * 重新索引文档：清除旧分块和向量，重置状态并触发入库管道。
     */
    @CacheEvict(value = {"searchQuestions", "searchAnswers", "searchCompanyInterviews", "searchResources"}, allEntries = true)
    @Transactional
    public void reindexDoc(String kbId, String docId, String userId, UserDetails currentUser) {
        KbKnowledgeBase kb = validateKbAccess(kbId, userId, currentUser);

        KbDocument doc = docRepo.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("文档不存在: " + docId));

        if (!doc.getKbId().equals(kbId)) {
            throw new BusinessException(400, "文档不属于该知识库");
        }

        String collectionName = kb.getMilvusCollection();

        // 1. 从 Milvus 删除旧向量
        if (collectionName != null && !collectionName.isBlank()) {
            try {
                milvusClient.delete(DeleteReq.builder()
                        .collectionName(collectionName)
                        .filter("doc_id == '" + docId + "'")
                        .build());
                log.info("Reindex: deleted old vectors from Milvus: collection={}, docId={}",
                        collectionName, docId);
            } catch (Exception e) {
                log.warn("Reindex: failed to delete vectors from Milvus {} for docId={}: {}",
                        collectionName, docId, e.getMessage());
            }
        }

        // 2. 删除 DB 旧分块
        long chunkCount = chunkRepo.countByDocId(docId);
        chunkRepo.deleteByDocId(docId);

        // 3. 更新知识库统计
        kb.setChunkCount(Math.max(0, (int) (kb.getChunkCount() - chunkCount)));
        kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
        kbRepo.save(kb);

        // 4. 重置文档状态
        doc.setStatus("UPLOADED");
        doc.setProgress(0);
        doc.setChunkCount(0);
        doc.setErrorMessage(null);
        doc.setIndexedAt(null);
        docRepo.save(doc);

        log.info("Reindex triggered: docId={}, kbId={}, oldChunks={}", docId, kbId, chunkCount);

        // 5. 事务提交后通过事件触发异步入库管道
        eventPublisher.publishEvent(new DocumentIngestionEvent(this, docId));
    }

    /**
     * 向量检索测试。
     */
    public SearchTestResponse searchKb(String kbId, String query, String filterExpr, Integer topK,
                                        String userId, UserDetails currentUser) {
        KbKnowledgeBase kb = validateKbAccess(kbId, userId, currentUser);

        String collectionName = kb.getMilvusCollection();
        if (collectionName == null || collectionName.isBlank()) {
            throw new BusinessException(400, "知识库未关联 Milvus Collection");
        }

        int k = topK != null ? topK : 5;
        long start = System.currentTimeMillis();
        List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(collectionName, query, k);
        long latencyMs = System.currentTimeMillis() - start;

        // 补充 tags 信息
        for (SearchTestResponse.SearchHit hit : hits) {
            if (hit.getDocId() != null) {
                docRepo.findByDocId(hit.getDocId()).ifPresent(d -> hit.setTags(d.getTags()));
            }
        }

        SearchTestResponse resp = new SearchTestResponse();
        resp.setTotal(hits.size());
        resp.setLatencyMs(latencyMs);
        resp.setHits(hits);

        log.info("KB search: kbId={}, query={}, topK={}, results={}, latencyMs={}",
                kbId, query, k, hits.size(), latencyMs);
        return resp;
    }
}
