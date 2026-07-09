/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.converter.KbConverter;
import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import com.tutorial.offerpilot.dto.tool.QuestionSearchResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import com.tutorial.offerpilot.exception.ResourceNotFoundException;
import com.tutorial.offerpilot.repository.ChunkRepository;
import com.tutorial.offerpilot.repository.DocumentRepository;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.KnowledgeBaseRepository;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

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

    private KnowledgeBaseService kbService;

    private static final String USER_ID = "u-test001";
    private static final String KB_ID = "kb-test";

    private UserDetails normalUser;
    private UserDetails adminUser;

    @BeforeEach
    void setUp() {
        kbService = new KnowledgeBaseService(kbRepo, docRepo, chunkRepo,
                milvusClient, kbConverter, vectorSearchService, questionRepo);

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
        @DisplayName("正常删除 → 级联删除 Milvus/Chunk/Doc/KB")
        void deleteKnowledgeBase_shouldCascadeDelete() {
            KbKnowledgeBase kb = buildKb(KB_ID, "PUBLIC");
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.of(kb));
            when(chunkRepo.countByKbId(KB_ID)).thenReturn(5L);
            when(docRepo.countByKbId(KB_ID)).thenReturn(2L);

            kbService.deleteKnowledgeBase(KB_ID);

            verify(chunkRepo).deleteByKbId(KB_ID);
            verify(docRepo).deleteByKbId(KB_ID);
            verify(kbRepo).delete(kb);
        }

        @Test
        @DisplayName("知识库不存在 → 抛 ResourceNotFoundException")
        void deleteKnowledgeBase_notFound_shouldThrow() {
            when(kbRepo.findByKbId(KB_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> kbService.deleteKnowledgeBase(KB_ID));
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

            kbService.deleteKnowledgeBase(KB_ID);

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
            when(vectorSearchService.search(anyString(), eq("算法"), eq(10))).thenReturn(Collections.emptyList());

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
}
