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
import com.tutorial.offerpilot.dto.tool.QuestionSearchResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseService 单元测试")
class KnowledgeBaseServiceTest {

    @Mock private KnowledgeBaseRepository kbRepo;
    @Mock private DocumentRepository docRepo;
    @Mock private ChunkRepository chunkRepo;
    @Mock private MilvusClientV2 milvusClient;
    @Mock private KbConverter kbConverter;
    @Mock private VectorSearchService vectorSearchService;
    @Mock private InterviewQuestionRepository questionRepo;
    @Mock private FileService fileService;
    @Mock private DocumentIngestionService ingestionService;
    @Mock private WebSearchFallbackService webSearchFallbackService;
    @Mock private PersonalizedRankService personalizedRankService;
    @Mock private SearchAnalyticsService searchAnalyticsService;
    @Mock private MilvusCollectionManager milvusCollectionManager;
    @Mock private ApplicationEventPublisher eventPublisher;

    private KnowledgeBaseService kbService;

    private static final String USER_ID = "u-test001";
    private static final String KB_ID = "kb-test";

    private UserDetails normalUser;
    private UserDetails adminUser;

    @BeforeEach
    void setUp() {
        kbService = new KnowledgeBaseService(kbRepo, docRepo, chunkRepo,
                milvusClient, kbConverter, vectorSearchService, questionRepo,
                fileService, ingestionService, webSearchFallbackService,
                personalizedRankService, searchAnalyticsService,
                milvusCollectionManager, eventPublisher);

        normalUser = new User("testuser", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        adminUser = new User("admin", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private KbKnowledgeBase buildKb(String kbId, String visibility) {
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setKbId(kbId);
        kb.setName("测试知识库");
        kb.setMilvusCollection("kb_" + kbId);
        kb.setCategory("技术");
        kb.setVisibility(visibility);
        kb.setStatus("ACTIVE");
        return kb;
    }

    // ==================== createKnowledgeBase ====================

    @Nested
    @DisplayName("createKnowledgeBase")
    class CreateTests {

        @Test
        @DisplayName("Admin 创建 → visibility=PUBLIC，ownerId=null")
        void create_asAdmin_shouldSetPublic() {
            CreateKbRequest req = new CreateKbRequest();
            req.setName("Admin库");
            req.setDescription("描述");
            req.setCategory("技术");

            KbKnowledgeBase kb = buildKb("kb-any", "PUBLIC");
            KbResponse resp = new KbResponse();
            resp.setKbId("kb-any");

            when(kbRepo.save(any())).thenReturn(kb);
            when(kbConverter.toResponse(any())).thenReturn(resp);

            KbResponse result = kbService.createKnowledgeBase(req, USER_ID, adminUser);

            assertNotNull(result);
            ArgumentCaptor<KbKnowledgeBase> captor = ArgumentCaptor.forClass(KbKnowledgeBase.class);
            verify(kbRepo).save(captor.capture());
            KbKnowledgeBase saved = captor.getValue();
            assertEquals("Admin库", saved.getName());
            assertEquals("PUBLIC", saved.getVisibility());
            assertNull(saved.getOwnerId());
            assertTrue(saved.getKbId().startsWith("kb-"));
        }

        @Test
        @DisplayName("普通用户创建 → visibility=PRIVATE，ownerId=userId")
        void create_asNormalUser_shouldSetPrivate() {
            CreateKbRequest req = new CreateKbRequest();
            req.setName("我的库");

            when(kbRepo.save(any())).thenReturn(buildKb("kb-abc", "PRIVATE"));
            when(kbConverter.toResponse(any())).thenReturn(new KbResponse());

            kbService.createKnowledgeBase(req, USER_ID, normalUser);

            ArgumentCaptor<KbKnowledgeBase> captor = ArgumentCaptor.forClass(KbKnowledgeBase.class);
            verify(kbRepo).save(captor.capture());
            assertEquals("PRIVATE", captor.getValue().getVisibility());
            assertEquals(USER_ID, captor.getValue().getOwnerId());
        }
    }

    // ==================== listKnowledgeBases ====================

    @Nested
    @DisplayName("listKnowledgeBases")
    class ListTests {

        @Test
        @DisplayName("正常 → 返回列表")
        void listKnowledgeBases_shouldReturnList() {
            KbKnowledgeBase kb1 = buildKb("kb-1", "PUBLIC");
            KbKnowledgeBase kb2 = buildKb("kb-2", "PRIVATE");
            when(kbRepo.findPublicOrOwnedBy(USER_ID)).thenReturn(List.of(kb1, kb2));
            when(kbConverter.toResponse(any())).thenReturn(new KbResponse());

            List<KbResponse> result = kbService.listKnowledgeBases(USER_ID, normalUser);

            assertEquals(2, result.size());
            verify(kbConverter, times(2)).toResponse(any());
        }

        @Test
        @DisplayName("无数据 → 返回空列表")
        void listKnowledgeBases_empty_shouldReturnEmptyList() {
            when(kbRepo.findPublicOrOwnedBy(USER_ID)).thenReturn(Collections.emptyList());

            List<KbResponse> result = kbService.listKnowledgeBases(USER_ID, normalUser);

            assertTrue(result.isEmpty());
        }
    }

    // ==================== deleteKnowledgeBase ====================

    @Nested
    @DisplayName("deleteKnowledgeBase")
    class DeleteTests {

        @Test
        @DisplayName("Admin 删除 → 级联删除 Milvus/Chunk/Doc/KB")
        void deleteKnowledgeBase_asAdmin_shouldCascadeDelete() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            kb.setOwnerId(null);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));
            when(chunkRepo.countByKbId(KB_ID)).thenReturn(5L);
            when(docRepo.countByKbId(KB_ID)).thenReturn(2L);

            kbService.deleteKnowledgeBase(KB_ID, "admin", adminUser);

            verify(chunkRepo).deleteByKbId(KB_ID);
            verify(docRepo).deleteByKbId(KB_ID);
            verify(kbRepo).delete(kb);
        }

        @Test
        @DisplayName("普通用户删除自己的 KB → 成功")
        void deleteKnowledgeBase_asOwner_shouldSucceed() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));
            when(chunkRepo.countByKbId(KB_ID)).thenReturn(0L);
            when(docRepo.countByKbId(KB_ID)).thenReturn(0L);

            kbService.deleteKnowledgeBase(KB_ID, USER_ID, normalUser);

            verify(kbRepo).delete(kb);
        }

        @Test
        @DisplayName("普通用户删除他人的 KB → 抛 BusinessException 403")
        void deleteKnowledgeBase_asNonOwner_shouldThrow403() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId("other-user");
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> kbService.deleteKnowledgeBase(KB_ID, USER_ID, normalUser));
            assertEquals(403, ex.getErrorCode());
            assertEquals("无权删除此知识库", ex.getMessage());
            verify(chunkRepo, never()).deleteByKbId(any());
        }

        @Test
        @DisplayName("普通用户删除 PUBLIC 库（ownerId=null）→ 抛 BusinessException 403")
        void deleteKnowledgeBase_publicKbByNonAdmin_shouldThrow403() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            kb.setOwnerId(null);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> kbService.deleteKnowledgeBase(KB_ID, USER_ID, normalUser));
            assertEquals(403, ex.getErrorCode());
            verify(chunkRepo, never()).deleteByKbId(any());
        }

        @Test
        @DisplayName("知识库不存在 → 抛 ResourceNotFoundException")
        void deleteKnowledgeBase_notFound_shouldThrow() {
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.deleteKnowledgeBase(KB_ID, USER_ID, normalUser));
            verify(chunkRepo, never()).deleteByKbId(any());
        }

        @Test
        @DisplayName("无 Milvus Collection → 跳过 Milvus 删除，仍执行其余清理")
        void deleteKnowledgeBase_noCollection_shouldSkipMilvus() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            kb.setMilvusCollection(null);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));
            when(chunkRepo.countByKbId(KB_ID)).thenReturn(0L);
            when(docRepo.countByKbId(KB_ID)).thenReturn(0L);

            kbService.deleteKnowledgeBase(KB_ID, "admin", adminUser);

            verifyNoInteractions(milvusClient);
            verify(chunkRepo).deleteByKbId(KB_ID);
            verify(docRepo).deleteByKbId(KB_ID);
            verify(kbRepo).delete(kb);
        }
    }

    // ==================== searchQuestions (fallback path) ====================

    @Nested
    @DisplayName("searchQuestions")
    class SearchQuestionsTests {

        @Test
        @DisplayName("Milvus 无结果 → 回退到 DB 面试题表")
        void searchQuestions_milvusEmpty_shouldFallbackToDb() {
            KbKnowledgeBase kb = buildKb("kb-1", "PUBLIC");
            when(kbRepo.findByVisibility("PUBLIC")).thenReturn(List.of(kb));
            SearchTestResponse searchResp = new SearchTestResponse();
            searchResp.setHits(Collections.emptyList());
            when(vectorSearchService.searchMultiCollection(anyList(), eq("算法"), eq(10), eq(20), isNull()))
                    .thenReturn(Collections.emptyList());

            InterviewQuestion q = new InterviewQuestion();
            q.setQuestionId("q-001");
            q.setQuestionText("算法题：反转链表");
            when(questionRepo.findAll()).thenReturn(List.of(q));

            QuestionSearchResult result = kbService.searchQuestions("算法");

            assertEquals(1, result.getTotal());
            assertEquals("q-001", result.getQuestions().get(0).getQuestionId());
        }

        @Test
        @DisplayName("所有 KB 都没有 Collection → 直接回退 DB")
        void searchQuestions_noCollection_shouldFallbackToDb() {
            KbKnowledgeBase kb = buildKb("kb-1", "PUBLIC");
            kb.setMilvusCollection(null);
            when(kbRepo.findByVisibility("PUBLIC")).thenReturn(List.of(kb));
            when(questionRepo.findAll()).thenReturn(Collections.emptyList());

            QuestionSearchResult result = kbService.searchQuestions("随便");

            assertEquals(0, result.getTotal());
            verifyNoInteractions(vectorSearchService);
        }
    }

    // ==================== searchAnswers (fallback) ====================

    @Nested
    @DisplayName("searchAnswers")
    class SearchAnswersTests {

        @Test
        @DisplayName("DB 回退 → 有答案的题目纳入结果")
        void searchAnswers_fallbackToDb() {
            when(kbRepo.findByVisibility("PUBLIC")).thenReturn(Collections.emptyList());

            InterviewQuestion q = new InterviewQuestion();
            q.setQuestionId("q-001");
            q.setQuestionText("Java 内存模型");
            q.setAnswerText("JMM 定义了...");
            when(questionRepo.findAll()).thenReturn(List.of(q));

            var result = kbService.searchAnswers("Java");

            assertEquals(1, result.getTotal());
            assertEquals("q-001", result.getAnswers().get(0).getAnswerId());
            assertTrue(result.getAnswers().get(0).getAnswer().contains("JMM"));
        }

        @Test
        @DisplayName("无匹配 → 返回空")
        void searchAnswers_noMatch_shouldReturnEmpty() {
            when(kbRepo.findByVisibility("PUBLIC")).thenReturn(Collections.emptyList());

            InterviewQuestion q = new InterviewQuestion();
            q.setQuestionText("Python 基础");
            q.setAnswerText("...");
            when(questionRepo.findAll()).thenReturn(List.of(q));

            var result = kbService.searchAnswers("Java");

            assertEquals(0, result.getTotal());
        }
    }

    // ==================== getKbStats ====================

    @Nested
    @DisplayName("getKbStats")
    class GetKbStatsTests {

        @Test
        @DisplayName("Admin → 返回统计信息")
        void getKbStats_asAdmin_shouldReturnStats() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            kb.setDocumentCount(5);
            kb.setChunkCount(50);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));
            when(docRepo.countByKbIdAndStatus(KB_ID, "ACTIVE")).thenReturn(3L);
            when(docRepo.countByKbIdAndStatus(KB_ID, "FAILED")).thenReturn(2L);

            KbStatsResponse result = kbService.getKbStats(KB_ID, "admin", adminUser);

            assertEquals(KB_ID, result.getKbId());
            assertEquals(5, result.getDocumentCount());
            assertEquals(50, result.getChunkCount());
            assertEquals(3L, result.getActiveDocuments());
            assertEquals(2L, result.getFailedDocuments());
        }

        @Test
        @DisplayName("KB 不存在 → 抛 ResourceNotFoundException")
        void getKbStats_notFound_shouldThrow() {
            when(kbRepo.findByKbId("kb-unknown")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.getKbStats("kb-unknown", USER_ID, normalUser));
        }

        @Test
        @DisplayName("非 owner 普通用户 → 抛 BusinessException 403")
        void getKbStats_nonOwner_shouldThrow403() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId("other-user");
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> kbService.getKbStats(KB_ID, USER_ID, normalUser));
            assertEquals(403, ex.getErrorCode());
        }
    }

    // ==================== listDocs ====================

    @Nested
    @DisplayName("listDocs")
    class ListDocsTests {

        @Test
        @DisplayName("正常 → 返回文档列表")
        void listDocs_shouldReturnDocList() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc1 = new KbDocument();
            doc1.setDocId("doc-1");
            KbDocument doc2 = new KbDocument();
            doc2.setDocId("doc-2");
            when(docRepo.findByKbId(KB_ID)).thenReturn(List.of(doc1, doc2));

            DocResponse resp1 = new DocResponse();
            resp1.setDocId("doc-1");
            DocResponse resp2 = new DocResponse();
            resp2.setDocId("doc-2");
            when(kbConverter.toDocResponse(doc1)).thenReturn(resp1);
            when(kbConverter.toDocResponse(doc2)).thenReturn(resp2);

            List<DocResponse> result = kbService.listDocs(KB_ID, USER_ID, normalUser);

            assertEquals(2, result.size());
            assertEquals("doc-1", result.get(0).getDocId());
            assertEquals("doc-2", result.get(1).getDocId());
            verify(docRepo).findByKbId(KB_ID);
        }

        @Test
        @DisplayName("KB 不存在 → 抛 ResourceNotFoundException")
        void listDocs_notFound_shouldThrow() {
            when(kbRepo.findByKbId("kb-unknown")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.listDocs("kb-unknown", USER_ID, normalUser));
        }
    }

    // ==================== getDocDetail ====================

    @Nested
    @DisplayName("getDocDetail")
    class GetDocDetailTests {

        private static final String DOC_ID = "doc-test";

        @Test
        @DisplayName("正常 → 返回文档详情含分块预览")
        void getDocDetail_shouldReturnDetailWithChunks() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId(KB_ID);
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            List<KbChunk> chunks = List.of(new KbChunk(), new KbChunk());
            when(chunkRepo.findByDocIdOrderByChunkIndex(DOC_ID)).thenReturn(chunks);

            DocDetailResponse expected = new DocDetailResponse();
            when(kbConverter.toDocDetailResponse(eq(doc), anyList())).thenReturn(expected);

            DocDetailResponse result = kbService.getDocDetail(KB_ID, DOC_ID, USER_ID, normalUser);

            assertNotNull(result);
            verify(kbConverter).toDocDetailResponse(eq(doc), anyList());
        }

        @Test
        @DisplayName("分块数 > 50 → 截断至前 50")
        void getDocDetail_manyChunks_shouldTruncate() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId(KB_ID);
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            List<KbChunk> chunks = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                chunks.add(new KbChunk());
            }
            when(chunkRepo.findByDocIdOrderByChunkIndex(DOC_ID)).thenReturn(chunks);

            when(kbConverter.toDocDetailResponse(eq(doc), anyList())).thenReturn(new DocDetailResponse());

            kbService.getDocDetail(KB_ID, DOC_ID, USER_ID, normalUser);

            ArgumentCaptor<List<KbChunk>> captor = ArgumentCaptor.forClass(List.class);
            verify(kbConverter).toDocDetailResponse(eq(doc), captor.capture());
            assertEquals(50, captor.getValue().size());
        }

        @Test
        @DisplayName("文档不属于该 KB → 抛 BusinessException 400")
        void getDocDetail_wrongKb_shouldThrow400() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId("other-kb");
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> kbService.getDocDetail(KB_ID, DOC_ID, USER_ID, normalUser));
            assertEquals(400, ex.getErrorCode());
            assertEquals("文档不属于该知识库", ex.getMessage());
        }

        @Test
        @DisplayName("文档不存在 → 抛 ResourceNotFoundException")
        void getDocDetail_docNotFound_shouldThrow() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.getDocDetail(KB_ID, DOC_ID, USER_ID, normalUser));
        }
    }

    // ==================== createDoc ====================

    @Nested
    @DisplayName("createDoc")
    class CreateDocTests {

        @Test
        @DisplayName("正常创建 → 保存文档并触发异步入库")
        void createDoc_shouldSaveAndTriggerIngestion() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            DocResponse resp = new DocResponse();
            resp.setDocId("doc-new");
            when(kbConverter.toDocResponse(any(KbDocument.class))).thenReturn(resp);

            DocResponse result = kbService.createDoc(KB_ID, "test.pdf", "/tmp/test.pdf",
                    "pdf", 1024L, "AUTO", USER_ID, normalUser);

            assertEquals("doc-new", result.getDocId());

            ArgumentCaptor<KbDocument> captor = ArgumentCaptor.forClass(KbDocument.class);
            verify(docRepo).save(captor.capture());
            KbDocument saved = captor.getValue();
            assertEquals(KB_ID, saved.getKbId());
            assertEquals("test.pdf", saved.getFileName());
            assertEquals("UPLOADED", saved.getStatus());
            assertEquals(0, saved.getProgress());
            assertTrue(saved.getDocId().startsWith("doc-"));

            verify(eventPublisher).publishEvent(any(DocumentIngestionEvent.class));
        }

        @Test
        @DisplayName("KB 不存在 → 抛 ResourceNotFoundException")
        void createDoc_notFound_shouldThrow() {
            when(kbRepo.findByKbId("kb-unknown")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.createDoc("kb-unknown", "f.txt", "/tmp/f.txt",
                            "txt", 100L, "AUTO", USER_ID, normalUser));
            verify(docRepo, never()).save(any());
            verifyNoInteractions(ingestionService);
        }
    }

    // ==================== deleteDoc ====================

    @Nested
    @DisplayName("deleteDoc")
    class DeleteDocTests {

        private static final String DOC_ID = "doc-del";

        @Test
        @DisplayName("Admin → 级联删除向量 + 分块 + 文档 + 更新统计")
        void deleteDoc_asAdmin_shouldCascadeDelete() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            kb.setDocumentCount(3);
            kb.setChunkCount(30);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId(KB_ID);
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            when(chunkRepo.countByDocId(DOC_ID)).thenReturn(5L);

            kbService.deleteDoc(KB_ID, DOC_ID, "admin", adminUser);

            verify(chunkRepo).deleteByDocId(DOC_ID);
            verify(docRepo).delete(doc);
            verify(kbRepo).save(kb);
            assertEquals(2, kb.getDocumentCount());
            assertEquals(25, kb.getChunkCount());
        }

        @Test
        @DisplayName("文档不属于该 KB → 抛 BusinessException 400")
        void deleteDoc_wrongKb_shouldThrow400() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId("other-kb");
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> kbService.deleteDoc(KB_ID, DOC_ID, "admin", adminUser));
            assertEquals(400, ex.getErrorCode());
            verify(chunkRepo, never()).deleteByDocId(any());
        }

        @Test
        @DisplayName("Milvus Collection 为空 → 跳过 Milvus，仍执行 DB 清理")
        void deleteDoc_noCollection_shouldSkipMilvus() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            kb.setMilvusCollection(null);
            kb.setDocumentCount(2);
            kb.setChunkCount(20);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId(KB_ID);
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            when(chunkRepo.countByDocId(DOC_ID)).thenReturn(3L);

            kbService.deleteDoc(KB_ID, DOC_ID, "admin", adminUser);

            verify(chunkRepo).deleteByDocId(DOC_ID);
            verify(docRepo).delete(doc);
            verify(kbRepo).save(kb);
            assertEquals(1, kb.getDocumentCount());
            assertEquals(17, kb.getChunkCount());
        }
    }

    // ==================== getDocProgress ====================

    @Nested
    @DisplayName("getDocProgress")
    class GetDocProgressTests {

        private static final String DOC_ID = "doc-progress";

        @Test
        @DisplayName("正常 → 返回进度（status + progress）")
        void getDocProgress_shouldReturnProgress() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId(KB_ID);
            doc.setStatus("INDEXING");
            doc.setProgress(80);
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            DocProgress result = kbService.getDocProgress(KB_ID, DOC_ID, USER_ID, normalUser);

            assertEquals("INDEXING", result.getStatus());
            assertEquals(80, result.getProgress());
        }

        @Test
        @DisplayName("文档不存在 → 抛 ResourceNotFoundException")
        void getDocProgress_docNotFound_shouldThrow() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.getDocProgress(KB_ID, DOC_ID, USER_ID, normalUser));
        }
    }

    // ==================== reindexDoc ====================

    @Nested
    @DisplayName("reindexDoc")
    class ReindexDocTests {

        private static final String DOC_ID = "doc-reindex";

        @Test
        @DisplayName("正常 → 删除旧数据 + 重置状态 + 触发入库")
        void reindexDoc_shouldResetAndTriggerIngestion() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            kb.setDocumentCount(3);
            kb.setChunkCount(30);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            KbDocument doc = new KbDocument();
            doc.setDocId(DOC_ID);
            doc.setKbId(KB_ID);
            doc.setStatus("ACTIVE");
            doc.setProgress(100);
            doc.setChunkCount(10);
            when(docRepo.findByDocId(DOC_ID)).thenReturn(Optional.of(doc));

            when(chunkRepo.countByDocId(DOC_ID)).thenReturn(10L);

            kbService.reindexDoc(KB_ID, DOC_ID, USER_ID, normalUser);

            verify(chunkRepo).deleteByDocId(DOC_ID);
            verify(kbRepo).save(kb);
            verify(docRepo).save(doc);
            assertEquals("UPLOADED", doc.getStatus());
            assertEquals(0, doc.getProgress());
            assertEquals(0, doc.getChunkCount());
            assertNull(doc.getErrorMessage());
            assertNull(doc.getIndexedAt());
            assertEquals(2, kb.getDocumentCount());
            assertEquals(20, kb.getChunkCount());
            verify(eventPublisher).publishEvent(any(DocumentIngestionEvent.class));
        }

        @Test
        @DisplayName("KB 不存在 → 抛 ResourceNotFoundException")
        void reindexDoc_notFound_shouldThrow() {
            when(kbRepo.findByKbId("kb-unknown")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.reindexDoc("kb-unknown", DOC_ID, USER_ID, normalUser));
            verifyNoInteractions(ingestionService);
        }
    }

    // ==================== searchKb ====================

    @Nested
    @DisplayName("searchKb")
    class SearchKbTests {

        @Test
        @DisplayName("正常检索 → 返回 SearchTestResponse")
        void searchKb_shouldReturnSearchResult() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            SearchTestResponse.SearchHit hit = new SearchTestResponse.SearchHit();
            hit.setDocId("doc-1");
            hit.setContent("测试内容");
            hit.setScore(0.5f);
            when(vectorSearchService.search(eq("kb_kb-test"), eq("查询文本"), eq(5)))
                    .thenReturn(List.of(hit));

            KbDocument hitDoc = new KbDocument();
            hitDoc.setTags("java");
            when(docRepo.findByDocId("doc-1")).thenReturn(Optional.of(hitDoc));

            SearchTestResponse result = kbService.searchKb(KB_ID, "查询文本", null, 5,
                    USER_ID, normalUser);

            assertEquals(1, result.getTotal());
            assertTrue(result.getLatencyMs() >= 0);
            assertEquals(1, result.getHits().size());
            assertEquals("java", result.getHits().get(0).getTags());
        }

        @Test
        @DisplayName("KB 未关联 Milvus Collection → 抛 BusinessException 400")
        void searchKb_noCollection_shouldThrow400() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PRIVATE");
            kb.setOwnerId(USER_ID);
            kb.setMilvusCollection(null);
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> kbService.searchKb(KB_ID, "查询", null, 5, USER_ID, normalUser));
            assertEquals(400, ex.getErrorCode());
            assertEquals("知识库未关联 Milvus Collection", ex.getMessage());
        }

        @Test
        @DisplayName("KB 不存在 → 抛 ResourceNotFoundException")
        void searchKb_notFound_shouldThrow() {
            when(kbRepo.findByKbId("kb-unknown")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> kbService.searchKb("kb-unknown", "查询", null, 5, USER_ID, normalUser));
        }
    }
}
