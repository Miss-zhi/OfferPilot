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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                DocumentRepository docRepo,
                                ChunkRepository chunkRepo,
                                MilvusClientV2 milvusClient,
                                KbConverter kbConverter,
                                VectorSearchService vectorSearchService,
                                InterviewQuestionRepository questionRepo,
                                FileService fileService,
                                DocumentIngestionService ingestionService) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.milvusClient = milvusClient;
        this.kbConverter = kbConverter;
        this.vectorSearchService = vectorSearchService;
        this.questionRepo = questionRepo;
        this.fileService = fileService;
        this.ingestionService = ingestionService;
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
     * 搜索面试题库。
     * 优先从 Milvus 知识库检索，若无结果则回退到 InterviewQuestion 表。
     */
    public QuestionSearchResult searchQuestions(String keyword) {
        List<QuestionSearchResult.QuestionItem> items = new ArrayList<>();

        // 1. 尝试从知识库（Milvus）检索
        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() == null || kb.getMilvusCollection().isBlank()) {
                continue;
            }
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(
                        kb.getMilvusCollection(), keyword, 10);
                for (SearchTestResponse.SearchHit hit : hits) {
                    QuestionSearchResult.QuestionItem item = new QuestionSearchResult.QuestionItem();
                    item.setQuestionId(hit.getDocId());
                    item.setContent(truncate(hit.getContent(), 200));
                    item.setCategory(kb.getCategory() != null ? kb.getCategory() : "通用");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    items.add(item);
                }
            } catch (Exception e) {
                log.debug("Search KB collection {} failed: {}", kb.getMilvusCollection(), e.getMessage());
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
                items.add(item);
            }
        }

        log.info("searchQuestions: keyword={}, results={}", keyword, items.size());
        return new QuestionSearchResult(items.size(), items);
    }

    /**
     * 搜索优秀面试答案库。
     */
    public AnswerSearchResult searchAnswers(String keyword) {
        List<AnswerSearchResult.AnswerItem> items = new ArrayList<>();

        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() == null || kb.getMilvusCollection().isBlank()) {
                continue;
            }
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(
                        kb.getMilvusCollection(), keyword, 10);
                for (SearchTestResponse.SearchHit hit : hits) {
                    AnswerSearchResult.AnswerItem item = new AnswerSearchResult.AnswerItem();
                    item.setAnswerId(hit.getDocId());
                    item.setQuestion(keyword);
                    item.setAnswer(truncate(hit.getContent(), 300));
                    item.setCategory(kb.getCategory() != null ? kb.getCategory() : "通用");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    items.add(item);
                }
            } catch (Exception e) {
                log.debug("Search KB collection {} failed: {}", kb.getMilvusCollection(), e.getMessage());
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
                items.add(item);
            }
        }

        log.info("searchAnswers: keyword={}, results={}", keyword, items.size());
        return new AnswerSearchResult(items.size(), items);
    }

    /**
     * 搜索公司面试经验。
     */
    public CompanySearchResult searchCompanyInterviews(String companyName) {
        List<CompanySearchResult.CompanyItem> items = new ArrayList<>();

        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        for (KbKnowledgeBase kb : kbs) {
            // 按知识库名称或描述匹配公司名
            if (kb.getName() == null || !kb.getName().toLowerCase().contains(companyName.toLowerCase())) {
                continue;
            }
            if (kb.getMilvusCollection() == null || kb.getMilvusCollection().isBlank()) {
                continue;
            }
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(
                        kb.getMilvusCollection(), companyName + " 面试", 5);
                for (SearchTestResponse.SearchHit hit : hits) {
                    CompanySearchResult.CompanyItem item = new CompanySearchResult.CompanyItem();
                    item.setCompanyName(companyName);
                    item.setInterviewType(inferInterviewType(hit.getContent()));
                    item.setSummary(truncate(hit.getContent(), 200));
                    item.setDifficulty("中等");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    items.add(item);
                }
            } catch (Exception e) {
                log.debug("Search KB collection {} failed: {}", kb.getMilvusCollection(), e.getMessage());
            }
        }

        log.info("searchCompanyInterviews: company={}, results={}", companyName, items.size());
        return new CompanySearchResult(items.size(), items);
    }

    /**
     * 搜索学习资源。
     */
    public ResourceListResult searchResources(String topic) {
        List<ResourceListResult.ResourceItem> items = new ArrayList<>();

        List<KbKnowledgeBase> kbs = kbRepo.findByVisibility("PUBLIC");
        for (KbKnowledgeBase kb : kbs) {
            if (kb.getMilvusCollection() == null || kb.getMilvusCollection().isBlank()) {
                continue;
            }
            try {
                List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(
                        kb.getMilvusCollection(), topic, 10);
                for (SearchTestResponse.SearchHit hit : hits) {
                    ResourceListResult.ResourceItem item = new ResourceListResult.ResourceItem();
                    item.setTitle(truncate(hit.getContent(), 80));
                    item.setUrl("");
                    item.setType(kb.getCategory() != null ? kb.getCategory() : "文档");
                    item.setRelevanceScore(1.0f / (1.0f + hit.getScore()));
                    items.add(item);
                }
            } catch (Exception e) {
                log.debug("Search KB collection {} failed: {}", kb.getMilvusCollection(), e.getMessage());
            }
        }

        log.info("searchResources: topic={}, results={}", topic, items.size());
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
