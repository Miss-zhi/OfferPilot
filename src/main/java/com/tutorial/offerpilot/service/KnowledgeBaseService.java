/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.converter.KbConverter;
import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import com.tutorial.offerpilot.dto.tool.AnswerSearchResult;
import com.tutorial.offerpilot.dto.tool.CompanySearchResult;
import com.tutorial.offerpilot.dto.tool.QuestionSearchResult;
import com.tutorial.offerpilot.dto.tool.ResourceListResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import com.tutorial.offerpilot.exception.ResourceNotFoundException;
import com.tutorial.offerpilot.repository.ChunkRepository;
import com.tutorial.offerpilot.repository.DocumentRepository;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.KnowledgeBaseRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public KnowledgeBaseService(KnowledgeBaseRepository kbRepo,
                                DocumentRepository docRepo,
                                ChunkRepository chunkRepo,
                                MilvusClientV2 milvusClient,
                                KbConverter kbConverter,
                                VectorSearchService vectorSearchService,
                                InterviewQuestionRepository questionRepo) {
        this.kbRepo = kbRepo;
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.milvusClient = milvusClient;
        this.kbConverter = kbConverter;
        this.vectorSearchService = vectorSearchService;
        this.questionRepo = questionRepo;
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
    public void deleteKnowledgeBase(String kbId) {
        KbKnowledgeBase kb = kbRepo.findByKbId(kbId)
                .orElseThrow(() -> new ResourceNotFoundException("知识库不存在: " + kbId));

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
}
