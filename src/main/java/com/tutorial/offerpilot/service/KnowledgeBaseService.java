/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.converter.KbConverter;
import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.DocDetailResponse;
import com.tutorial.offerpilot.dto.kb.DocProgress;
import com.tutorial.offerpilot.dto.kb.DocResponse;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.dto.kb.KbStatsResponse;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    private final WebSearchFallbackService webSearchFallbackService;
    private final PersonalizedRankService personalizedRankService;
    private final SearchAnalyticsService searchAnalyticsService;
    private final MilvusCollectionManager milvusCollectionManager;

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                DocumentRepository docRepo,
                                ChunkRepository chunkRepo,
                                MilvusClientV2 milvusClient,
                                KbConverter kbConverter,
                                VectorSearchService vectorSearchService,
                                InterviewQuestionRepository questionRepo,
                                FileService fileService,
                                DocumentIngestionService ingestionService,
                                WebSearchFallbackService webSearchFallbackService,
                                PersonalizedRankService personalizedRankService,
                                SearchAnalyticsService searchAnalyticsService,
                                MilvusCollectionManager milvusCollectionManager) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.milvusClient = milvusClient;
        this.kbConverter = kbConverter;
        this.vectorSearchService = vectorSearchService;
        this.questionRepo = questionRepo;
        this.fileService = fileService;
        this.ingestionService = ingestionService;
        this.webSearchFallbackService = webSearchFallbackService;
        this.personalizedRankService = personalizedRankService;
        this.searchAnalyticsService = searchAnalyticsService;
        this.milvusCollectionManager = milvusCollectionManager;
    }

    @Transactional
    public KbResponse createKnowledgeBase(CreateKbRequest req, String userId, UserDetails currentUser) {
        String kbId = "kb-" + UUID.randomUUID().toString().substring(0, 8);
        String collectionName = "kb_" + kbId;
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
     * 优先从 Milvus 知识库多 Collection 合并检索，支持元数据过滤，
     * 若无结果则回退到 InterviewQuestion 表，最终 MCP 联网兜底。
     */
    public QuestionSearchResult searchQuestions(SearchRequest req) {
        String keyword = req.getKeywords();
        String filterExpr = req.buildFilterExpr();
        int topK = Math.min(req.getTopK() != null ? req.getTopK() : 10, 50);
        List<QuestionSearchResult.QuestionItem> items = new ArrayList<>();
        long start = System.currentTimeMillis();

        // 1. 收集 PUBLIC 知识库的 Collection，批量合并检索
        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        List<String> collections = new ArrayList<>();
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                collections.add(kb.getMilvusCollection());
            }
        }

        if (!collections.isEmpty()) {
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                        collections, keyword, topK, topK * 2, filterExpr);
                for (SearchTestResponse.SearchHit hit : hits) {
                    QuestionSearchResult.QuestionItem item = new QuestionSearchResult.QuestionItem();
                    item.setQuestionId(hit.getDocId());
                    item.setContent(truncate(hit.getContent(), 200));
                    item.setCategory("通用");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    item.setSource("kb");
                    items.add(item);
                }
            } catch (Exception e) {
                log.warn("searchQuestions multi-collection failed: {}", e.getMessage());
            }
        }

        // 2. 回退到数据库面试题表
        if (items.isEmpty()) {
            List<InterviewQuestion> questions = questionRepo.findAll().stream()
                    .filter(q -> q.getQuestionText() != null
                            && q.getQuestionText().toLowerCase().contains(keyword.toLowerCase()))
                    .limit(10)
                    .toList();
            for (InterviewQuestion q : questions) {
                QuestionSearchResult.QuestionItem item = new QuestionSearchResult.QuestionItem();
                item.setQuestionId(q.getQuestionId());
                item.setContent(q.getQuestionText());
                item.setCategory("面试题库");
                item.setRelevanceScore(0.8f);
                item.setSource("db");
                items.add(item);
            }
        }

        // 3. MCP 联网兜底
        if (items.isEmpty()) {
            List<WebSearchFallbackService.WebSearchItem> webItems = webSearchFallbackService.search(keyword);
            for (WebSearchFallbackService.WebSearchItem webItem : webItems) {
                QuestionSearchResult.QuestionItem item = new QuestionSearchResult.QuestionItem();
                item.setQuestionId("web-" + UUID.randomUUID().toString().substring(0, 8));
                item.setContent(truncate(webItem.getContent(), 200));
                item.setCategory("联网搜索");
                item.setRelevanceScore(0.6f);
                item.setSource("web");
                items.add(item);
            }
        }

        // 统计各来源命中数
        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = (int) items.stream().filter(i -> "db".equals(i.getSource())).count();
        int webHits = (int) items.stream().filter(i -> "web".equals(i.getSource())).count();

        // 个性化加权排序
        if (req.getUserId() != null && !req.getUserId().isBlank() && !items.isEmpty()) {
            Set<String> weakPoints = personalizedRankService.getWeakPoints(req.getUserId());
            for (QuestionSearchResult.QuestionItem item : items) {
                item.setRelevanceScore(personalizedRankService.boostScore(
                        item.getContent(), item.getRelevanceScore(), weakPoints));
            }
            items.sort((a, b) -> Float.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        }

        long latencyMs = System.currentTimeMillis() - start;
        // 异步记录搜索日志
        searchAnalyticsService.logSearch(req.getUserId(), keyword, "searchQuestions",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchQuestions: keyword={}, results={}, source={}, latencyMs={}",
                keyword, items.size(), items.isEmpty() ? "none" : items.get(0).getSource(), latencyMs);
        return new QuestionSearchResult(items.size(), items);
    }

    /**
     * 搜索优秀面试答案库（简化接口，向后兼容）。
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
     * 优先从 Milvus 多 Collection 合并检索，支持元数据过滤，
     * 若无结果则回退到已有面试记录，最终 MCP 联网兜底。
     */
    public AnswerSearchResult searchAnswers(SearchRequest req) {
        String keyword = req.getKeywords();
        String filterExpr = req.buildFilterExpr();
        int topK = Math.min(req.getTopK() != null ? req.getTopK() : 10, 50);
        List<AnswerSearchResult.AnswerItem> items = new ArrayList<>();
        long start = System.currentTimeMillis();

        // 1. 收集 PUBLIC 知识库的 Collection，批量合并检索
        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        List<String> collections = new ArrayList<>();
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                collections.add(kb.getMilvusCollection());
            }
        }

        if (!collections.isEmpty()) {
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                        collections, keyword, topK, topK * 2, filterExpr);
                for (SearchTestResponse.SearchHit hit : hits) {
                    AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
                    item.setAnswerId(hit.getDocId());
                    item.setQuestion(keyword);
                    item.setAnswer(truncate(hit.getContent(), 300));
                    item.setCategory("通用");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    item.setSource("kb");
                    items.add(item);
                }
            } catch (Exception e) {
                log.warn("searchAnswers multi-collection failed: {}", e.getMessage());
            }
        }

        // 如果知识库没有结果，尝试从已有面试记录中查找答案
        if (items.isEmpty()) {
            List<InterviewQuestion> questions = questionRepo.findAll().stream()
                    .filter(q -> q.getAnswerText() != null && !q.getAnswerText().isBlank())
                    .filter(q -> q.getQuestionText() != null
                            && q.getQuestionText().toLowerCase().contains(keyword.toLowerCase()))
                    .limit(10)
                    .toList();
            for (InterviewQuestion q : questions) {
                AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
                item.setAnswerId(q.getQuestionId());
                item.setQuestion(q.getQuestionText());
                item.setAnswer(truncate(q.getAnswerText(), 300));
                item.setCategory("面试答案库");
                item.setRelevanceScore(0.8f);
                item.setSource("db");
                items.add(item);
            }
        }

        // 如果知识库和DB均无结果，MCP 联网兜底
        if (items.isEmpty()) {
            List<WebSearchFallbackService.WebSearchItem> webItems = webSearchFallbackService.search(keyword);
            for (WebSearchFallbackService.WebSearchItem webItem : webItems) {
                AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
                item.setAnswerId("web-" + UUID.randomUUID().toString().substring(0, 8));
                item.setQuestion(keyword);
                item.setAnswer(truncate(webItem.getContent(), 300));
                item.setCategory("联网搜索");
                item.setRelevanceScore(0.6f);
                item.setSource("web");
                items.add(item);
            }
        }

        // 统计各来源命中数
        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = (int) items.stream().filter(i -> "db".equals(i.getSource())).count();
        int webHits = (int) items.stream().filter(i -> "web".equals(i.getSource())).count();

        // 个性化加权排序
        if (req.getUserId() != null && !req.getUserId().isBlank() && !items.isEmpty()) {
            Set<String> weakPoints = personalizedRankService.getWeakPoints(req.getUserId());
            for (AnswerSearchResult.AnswerItem item : items) {
                item.setRelevanceScore(personalizedRankService.boostScore(
                        item.getAnswer(), item.getRelevanceScore(), weakPoints));
            }
            items.sort((a, b) -> Float.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        }

        long latencyMs = System.currentTimeMillis() - start;
        // 异步记录搜索日志
        searchAnalyticsService.logSearch(req.getUserId(), keyword, "searchAnswers",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchAnswers: keyword={}, results={}, source={}, latencyMs={}",
                keyword, items.size(), items.isEmpty() ? "none" : items.get(0).getSource(), latencyMs);
        return new AnswerSearchResult(items.size(), items);
    }

    /**
     * 搜索公司面试经验（简化接口，向后兼容）。
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
     * 先过滤公司相关的 KB，再合并检索，支持元数据过滤，
     * 无结果时回退到 DB LIKE，最终 MCP 联网兜底。
     */
    public CompanySearchResult searchCompanyInterviews(SearchRequest req) {
        String companyName = req.getCompany() != null ? req.getCompany() : req.getKeywords();
        String filterExpr = req.buildFilterExpr();
        int topK = Math.min(req.getTopK() != null ? req.getTopK() : 10, 50);
        List<CompanySearchResult.CompanyItem> items = new ArrayList<>();
        long start = System.currentTimeMillis();

        // 1. 收集与公司名称匹配的 PUBLIC 知识库 Collection
        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        List<String> collections = new ArrayList<>();
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                // 按知识库名称或描述匹配公司名
                if (kb.getName() != null && kb.getName().toLowerCase().contains(companyName.toLowerCase())) {
                    collections.add(kb.getMilvusCollection());
                }
            }
        }

        // 2. 如果没有精确匹配公司名的 KB，则对所有 PUBLIC KB 检索
        if (collections.isEmpty()) {
            for (KbKnowledgeBase kb : kbs) {
                if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                    collections.add(kb.getMilvusCollection());
                }
            }
        }

        if (!collections.isEmpty()) {
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                        collections, companyName + " 面试", topK, topK * 2, filterExpr);
                for (SearchTestResponse.SearchHit hit : hits) {
                    CompanySearchResult.CompanyItem item = new CompanySearchResult.CompanyItem();
                    item.setCompanyName(companyName);
                    item.setInterviewType(inferInterviewType(hit.getContent()));
                    item.setSummary(truncate(hit.getContent(), 200));
                    item.setDifficulty("中等");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    item.setSource("kb");
                    items.add(item);
                }
            } catch (Exception e) {
                log.warn("searchCompanyInterviews multi-collection failed: {}", e.getMessage());
            }
        }

        // 3. DB 回退
        if (items.isEmpty()) {
            List<InterviewQuestion> questions = questionRepo.findAll().stream()
                    .filter(q -> q.getQuestionText() != null
                            && q.getQuestionText().toLowerCase().contains(companyName.toLowerCase()))
                    .limit(10)
                    .toList();
            for (InterviewQuestion q : questions) {
                CompanySearchResult.CompanyItem item = new CompanySearchResult.CompanyItem();
                item.setCompanyName(companyName);
                item.setInterviewType(inferInterviewType(q.getQuestionText()));
                item.setSummary(truncate(q.getQuestionText(), 200));
                item.setDifficulty("中等");
                item.setRelevanceScore(0.75f);
                item.setSource("db");
                items.add(item);
            }
        }

        // 4. MCP 联网兜底
        if (items.isEmpty()) {
            List<WebSearchFallbackService.WebSearchItem> webItems = webSearchFallbackService.search(companyName + " 面试经验");
            for (WebSearchFallbackService.WebSearchItem webItem : webItems) {
                CompanySearchResult.CompanyItem item = new CompanySearchResult.CompanyItem();
                item.setCompanyName(companyName);
                item.setInterviewType("综合面试");
                item.setSummary(truncate(webItem.getContent(), 200));
                item.setDifficulty("未知");
                item.setRelevanceScore(0.6f);
                item.setSource("web");
                items.add(item);
            }
        }

        // 统计各来源命中数
        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = (int) items.stream().filter(i -> "db".equals(i.getSource())).count();
        int webHits = (int) items.stream().filter(i -> "web".equals(i.getSource())).count();

        // 个性化加权排序
        if (req.getUserId() != null && !req.getUserId().isBlank() && !items.isEmpty()) {
            Set<String> weakPoints = personalizedRankService.getWeakPoints(req.getUserId());
            for (CompanySearchResult.CompanyItem item : items) {
                item.setRelevanceScore(personalizedRankService.boostScore(
                        item.getSummary(), item.getRelevanceScore(), weakPoints));
            }
            items.sort((a, b) -> Float.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        }

        long latencyMs = System.currentTimeMillis() - start;
        // 异步记录搜索日志
        searchAnalyticsService.logSearch(req.getUserId(), companyName, "searchCompanyInterviews",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchCompanyInterviews: company={}, results={}, source={}, latencyMs={}",
                companyName, items.size(), items.isEmpty() ? "none" : items.get(0).getSource(), latencyMs);
        return new CompanySearchResult(items.size(), items);
    }

    /**
     * 搜索学习资源（简化接口，向后兼容）。
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
     * 通过多 Collection 合并检索，支持元数据过滤，
     * 无结果时 MCP 联网兜底。
     */
    public ResourceListResult searchResources(SearchRequest req) {
        String topic = req.getKeywords();
        String filterExpr = req.buildFilterExpr();
        int topK = Math.min(req.getTopK() != null ? req.getTopK() : 10, 50);
        List<ResourceListResult.ResourceItem> items = new ArrayList<>();
        long start = System.currentTimeMillis();

        // 1. 收集 PUBLIC 知识库的 Collection，批量合并检索
        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        List<String> collections = new ArrayList<>();
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() != null && !kb.getMilvusCollection().isBlank()) {
                collections.add(kb.getMilvusCollection());
            }
        }

        if (!collections.isEmpty()) {
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                        collections, topic, topK, topK * 2, filterExpr);
                for (SearchTestResponse.SearchHit hit : hits) {
                    ResourceListResult.ResourceItem item = new ResourceListResult.ResourceItem();
                    item.setTitle(truncate(hit.getContent(), 80));
                    item.setUrl("");
                    item.setType("文档");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    item.setSource("kb");
                    items.add(item);
                }
            } catch (Exception e) {
                log.warn("searchResources multi-collection failed: {}", e.getMessage());
            }
        }

        // 2. MCP 联网兜底
        if (items.isEmpty()) {
            List<WebSearchFallbackService.WebSearchItem> webItems = webSearchFallbackService.search(topic + " 学习资源");
            for (WebSearchFallbackService.WebSearchItem webItem : webItems) {
                ResourceListResult.ResourceItem item = new ResourceListResult.ResourceItem();
                item.setTitle(truncate(webItem.getContent(), 80));
                item.setUrl("");
                item.setType("联网搜索");
                item.setRelevanceScore(0.6f);
                item.setSource("web");
                items.add(item);
            }
        }

        // 统计各来源命中数
        int milvusHits = (int) items.stream().filter(i -> "kb".equals(i.getSource())).count();
        int dbHits = (int) items.stream().filter(i -> "db".equals(i.getSource())).count();
        int webHits = (int) items.stream().filter(i -> "web".equals(i.getSource())).count();

        // 个性化加权排序
        if (req.getUserId() != null && !req.getUserId().isBlank() && !items.isEmpty()) {
            Set<String> weakPoints = personalizedRankService.getWeakPoints(req.getUserId());
            for (ResourceListResult.ResourceItem item : items) {
                item.setRelevanceScore(personalizedRankService.boostScore(
                        item.getTitle(), item.getRelevanceScore(), weakPoints));
            }
            items.sort((a, b) -> Float.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        }

        long latencyMs = System.currentTimeMillis() - start;
        // 异步记录搜索日志
        searchAnalyticsService.logSearch(req.getUserId(), topic, "searchResources",
                milvusHits, dbHits, webHits, latencyMs);
        log.info("searchResources: topic={}, results={}, source={}, latencyMs={}",
                topic, items.size(), items.isEmpty() ? "none" : items.get(0).getSource(), latencyMs);
        return new ResourceListResult(items.size(), items);
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

        // 异步触发入库管道
        ingestionService.ingestDocument(docId);

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

        // 5. 触发异步入库管道
        ingestionService.ingestDocument(docId);
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
