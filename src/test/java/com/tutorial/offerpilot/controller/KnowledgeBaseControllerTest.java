/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.DocDetailResponse;
import com.tutorial.offerpilot.dto.kb.DocProgress;
import com.tutorial.offerpilot.dto.kb.DocResponse;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.dto.kb.KbStatsResponse;
import com.tutorial.offerpilot.dto.kb.SearchTestRequest;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.service.FileService;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseController Web 层测试")
class KnowledgeBaseControllerTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgeBaseService kbService;

    @Mock
    private FileService fileService;

    @InjectMocks
    private KnowledgeBaseController controller;

    private static final String USER_ID = "testuser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        User user = new User(USER_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private KbResponse buildKbResponse(String kbId, String name) {
        KbResponse kb = new KbResponse();
        kb.setKbId(kbId);
        kb.setName(name);
        kb.setDescription("测试知识库");
        kb.setCategory("tech");
        kb.setVisibility("PRIVATE");
        kb.setStatus("ACTIVE");
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        return kb;
    }

    // ==================== POST /api/v1/kb ====================

    @Nested
    @DisplayName("POST /api/v1/kb")
    class CreateKbTests {

        @Test
        @DisplayName("正常创建 → 201 CREATED + KbResponse")
        void createKb_shouldReturn201() throws Exception {
            KbResponse kb = buildKbResponse("kb-001", "Java面试题库");
            when(kbService.createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(kb);

            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Java面试题库", "description": "涵盖Java基础与进阶", "category": "tech"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.kbId").value("kb-001"))
                    .andExpect(jsonPath("$.data.name").value("Java面试题库"))
                    .andExpect(jsonPath("$.data.description").value("测试知识库"))
                    .andExpect(jsonPath("$.data.category").value("tech"))
                    .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.documentCount").value(0));

            verify(kbService).createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("名称为空 → 400 参数校验失败")
        void createKb_blankName_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "", "description": "desc"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(kbService, never()).createKnowledgeBase(any(), anyString(), any());
        }

        @Test
        @DisplayName("仅 name 必填 → description 和 category 可选")
        void createKb_onlyName_shouldSucceed() throws Exception {
            KbResponse kb = buildKbResponse("kb-002", "最小知识库");
            kb.setDescription(null);
            kb.setCategory(null);
            when(kbService.createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(kb);

            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "最小知识库"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.kbId").value("kb-002"))
                    .andExpect(jsonPath("$.data.name").value("最小知识库"));
        }

        @Test
        @DisplayName("服务层异常 → 异常透传")
        void createKb_serviceError_shouldPropagate() throws Exception {
            when(kbService.createKnowledgeBase(any(CreateKbRequest.class), eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new BusinessException(409, "知识库名称已存在"));

            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "重复名称"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value("知识库名称已存在"));
        }
    }

    // ==================== GET /api/v1/kb ====================

    @Nested
    @DisplayName("GET /api/v1/kb")
    class ListKbTests {

        @Test
        @DisplayName("正常查询 → 200 + 知识库列表")
        void listKb_shouldReturn200() throws Exception {
            KbResponse kb1 = buildKbResponse("kb-001", "Java面试题库");
            KbResponse kb2 = buildKbResponse("kb-002", "系统设计题库");
            kb2.setDocumentCount(5);
            when(kbService.listKnowledgeBases(eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(List.of(kb1, kb2));

            mockMvc.perform(get("/api/v1/kb"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].kbId").value("kb-001"))
                    .andExpect(jsonPath("$.data[0].name").value("Java面试题库"))
                    .andExpect(jsonPath("$.data[1].kbId").value("kb-002"))
                    .andExpect(jsonPath("$.data[1].documentCount").value(5));

            verify(kbService).listKnowledgeBases(eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("无知识库 → 返回空列表")
        void listKb_empty_shouldReturnEmptyList() throws Exception {
            when(kbService.listKnowledgeBases(eq(USER_ID), any(UserDetails.class))).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/kb"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("服务层异常 → 500 内部错误")
        void listKb_serviceError_shouldReturn500() throws Exception {
            when(kbService.listKnowledgeBases(eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/api/v1/kb"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
        }
    }

    // ==================== DELETE /api/v1/kb/{kbId} ====================

    @Nested
    @DisplayName("DELETE /api/v1/kb/{kbId}")
    class DeleteKbTests {

        @Test
        @DisplayName("正常删除 → 204 No Content")
        void deleteKb_shouldReturn204() throws Exception {
            doNothing().when(kbService).deleteKnowledgeBase(eq("kb-001"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-001"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(kbService).deleteKnowledgeBase(eq("kb-001"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("知识库不存在 → 404")
        void deleteKb_notFound_shouldReturn404() throws Exception {
            doThrow(new BusinessException(404, "知识库不存在"))
                    .when(kbService).deleteKnowledgeBase(eq("kb-999"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("知识库不存在"));
        }

        @Test
        @DisplayName("无权删除 → 403 Forbidden")
        void deleteKb_forbidden_shouldReturn403() throws Exception {
            doThrow(new BusinessException(403, "无权删除此知识库"))
                    .when(kbService).deleteKnowledgeBase(eq("kb-others"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-others"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("无权删除此知识库"));
        }
    }

    // ==================== 辅助方法 ====================

    private DocResponse buildDocResponse(String docId, String kbId) {
        DocResponse doc = new DocResponse();
        doc.setDocId(docId);
        doc.setKbId(kbId);
        doc.setFileName("test.pdf");
        doc.setFileType("pdf");
        doc.setFileSize(1024L);
        doc.setChunkCount(0);
        doc.setChunkStrategy("AUTO");
        doc.setStatus("UPLOADED");
        doc.setProgress(0);
        return doc;
    }

    private KbStatsResponse buildStatsResponse(String kbId) {
        KbStatsResponse stats = new KbStatsResponse();
        stats.setKbId(kbId);
        stats.setName("测试库");
        stats.setDocumentCount(5);
        stats.setChunkCount(50);
        stats.setActiveDocuments(3L);
        stats.setFailedDocuments(2L);
        return stats;
    }

    private DocDetailResponse buildDocDetailResponse(String docId, String kbId) {
        DocDetailResponse detail = new DocDetailResponse();
        detail.setDocId(docId);
        detail.setKbId(kbId);
        detail.setFileName("test.pdf");
        detail.setFileType("pdf");
        detail.setStatus("ACTIVE");
        detail.setProgress(100);
        detail.setErrorMessage(null);
        detail.setChunks(List.of());
        return detail;
    }

    private DocProgress buildDocProgress(String status, Integer progress) {
        return new DocProgress(status, progress);
    }

    private SearchTestResponse buildSearchResponse(int total) {
        SearchTestResponse resp = new SearchTestResponse();
        resp.setTotal(total);
        resp.setLatencyMs(42L);
        resp.setHits(List.of());
        return resp;
    }

    // ==================== GET /{kbId}/stats ====================

    @Nested
    @DisplayName("GET /{kbId}/stats")
    class GetKbStatsTests {

        @Test
        @DisplayName("正常 → 200 + KbStatsResponse")
        void getKbStats_shouldReturn200() throws Exception {
            KbStatsResponse stats = buildStatsResponse("kb-001");
            when(kbService.getKbStats(eq("kb-001"), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(stats);

            mockMvc.perform(get("/api/v1/kb/kb-001/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.kbId").value("kb-001"))
                    .andExpect(jsonPath("$.data.name").value("测试库"))
                    .andExpect(jsonPath("$.data.documentCount").value(5))
                    .andExpect(jsonPath("$.data.chunkCount").value(50))
                    .andExpect(jsonPath("$.data.activeDocuments").value(3))
                    .andExpect(jsonPath("$.data.failedDocuments").value(2));

            verify(kbService).getKbStats(eq("kb-001"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("KB 不存在 → 404")
        void getKbStats_notFound_shouldReturn404() throws Exception {
            when(kbService.getKbStats(eq("kb-999"), eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new BusinessException(404, "知识库不存在"));

            mockMvc.perform(get("/api/v1/kb/kb-999/stats"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== GET /{kbId}/docs ====================

    @Nested
    @DisplayName("GET /{kbId}/docs")
    class ListDocsTests {

        @Test
        @DisplayName("正常 → 200 + 文档列表")
        void listDocs_shouldReturn200() throws Exception {
            DocResponse doc1 = buildDocResponse("doc-1", "kb-001");
            DocResponse doc2 = buildDocResponse("doc-2", "kb-001");
            doc2.setChunkCount(10);
            when(kbService.listDocs(eq("kb-001"), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(List.of(doc1, doc2));

            mockMvc.perform(get("/api/v1/kb/kb-001/docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].docId").value("doc-1"))
                    .andExpect(jsonPath("$.data[0].fileName").value("test.pdf"))
                    .andExpect(jsonPath("$.data[1].docId").value("doc-2"))
                    .andExpect(jsonPath("$.data[1].chunkCount").value(10));

            verify(kbService).listDocs(eq("kb-001"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("空列表 → 200 + []")
        void listDocs_empty_shouldReturnEmptyList() throws Exception {
            when(kbService.listDocs(eq("kb-001"), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/kb/kb-001/docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ==================== POST /{kbId}/docs ====================

    @Nested
    @DisplayName("POST /{kbId}/docs")
    class UploadDocTests {

        @Test
        @DisplayName("正常上传 → 201 + DocResponse")
        void uploadDoc_shouldReturn201() throws Exception {
            when(fileService.saveFile(any(), eq("kb-docs"))).thenReturn("/tmp/kb-docs/test.pdf");

            DocResponse doc = buildDocResponse("doc-new", "kb-001");
            when(kbService.createDoc(eq("kb-001"), eq("test.pdf"), anyString(), eq("pdf"),
                    anyLong(), eq("AUTO"), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(doc);

            mockMvc.perform(multipart("/api/v1/kb/kb-001/docs")
                            .file(new MockMultipartFile("file", "test.pdf", "application/pdf", "test content".getBytes()))
                            .param("chunkStrategy", "AUTO"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.docId").value("doc-new"))
                    .andExpect(jsonPath("$.data.kbId").value("kb-001"))
                    .andExpect(jsonPath("$.data.fileName").value("test.pdf"))
                    .andExpect(jsonPath("$.data.status").value("UPLOADED"));

            verify(kbService).createDoc(eq("kb-001"), eq("test.pdf"), anyString(),
                    eq("pdf"), anyLong(), eq("AUTO"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("默认 chunkStrategy=AUTO")
        void uploadDoc_defaultStrategy_shouldUseAuto() throws Exception {
            when(fileService.saveFile(any(), eq("kb-docs"))).thenReturn("/tmp/kb-docs/test.txt");

            DocResponse doc = buildDocResponse("doc-def", "kb-001");
            doc.setFileType("txt");
            when(kbService.createDoc(eq("kb-001"), eq("test.txt"), anyString(), eq("txt"),
                    anyLong(), eq("AUTO"), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(doc);

            mockMvc.perform(multipart("/api/v1/kb/kb-001/docs")
                            .file(new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.docId").value("doc-def"));
        }

        @Test
        @DisplayName("KB 不存在 → 404")
        void uploadDoc_notFound_shouldReturn404() throws Exception {
            when(fileService.saveFile(any(), eq("kb-docs"))).thenReturn("/tmp/kb-docs/test.txt");
            when(kbService.createDoc(eq("kb-999"), any(), any(), any(), anyLong(), any(),
                    eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new BusinessException(404, "知识库不存在"));

            mockMvc.perform(multipart("/api/v1/kb/kb-999/docs")
                            .file(new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== GET /{kbId}/docs/{docId} ====================

    @Nested
    @DisplayName("GET /{kbId}/docs/{docId}")
    class GetDocDetailTests {

        @Test
        @DisplayName("正常 → 200 + DocDetailResponse")
        void getDocDetail_shouldReturn200() throws Exception {
            DocDetailResponse detail = buildDocDetailResponse("doc-1", "kb-001");
            when(kbService.getDocDetail(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(detail);

            mockMvc.perform(get("/api/v1/kb/kb-001/docs/doc-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.docId").value("doc-1"))
                    .andExpect(jsonPath("$.data.kbId").value("kb-001"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.progress").value(100));

            verify(kbService).getDocDetail(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void getDocDetail_notFound_shouldReturn404() throws Exception {
            when(kbService.getDocDetail(eq("kb-001"), eq("doc-999"), eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new BusinessException(404, "文档不存在"));

            mockMvc.perform(get("/api/v1/kb/kb-001/docs/doc-999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== DELETE /{kbId}/docs/{docId} ====================

    @Nested
    @DisplayName("DELETE /{kbId}/docs/{docId}")
    class DeleteDocTests {

        @Test
        @DisplayName("正常删除 → 204 No Content")
        void deleteDoc_shouldReturn204() throws Exception {
            doNothing().when(kbService).deleteDoc(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-001/docs/doc-1"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(kbService).deleteDoc(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void deleteDoc_notFound_shouldReturn404() throws Exception {
            doThrow(new BusinessException(404, "文档不存在"))
                    .when(kbService).deleteDoc(eq("kb-001"), eq("doc-999"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(delete("/api/v1/kb/kb-001/docs/doc-999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== GET /{kbId}/docs/{docId}/progress ====================

    @Nested
    @DisplayName("GET /{kbId}/docs/{docId}/progress")
    class GetDocProgressTests {

        @Test
        @DisplayName("正常 → 200 + DocProgress")
        void getDocProgress_shouldReturn200() throws Exception {
            DocProgress progress = buildDocProgress("INDEXING", 80);
            when(kbService.getDocProgress(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(progress);

            mockMvc.perform(get("/api/v1/kb/kb-001/docs/doc-1/progress"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.status").value("INDEXING"))
                    .andExpect(jsonPath("$.data.progress").value(80));

            verify(kbService).getDocProgress(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void getDocProgress_notFound_shouldReturn404() throws Exception {
            when(kbService.getDocProgress(eq("kb-001"), eq("doc-999"), eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new BusinessException(404, "文档不存在"));

            mockMvc.perform(get("/api/v1/kb/kb-001/docs/doc-999/progress"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== POST /{kbId}/docs/{docId}/reindex ====================

    @Nested
    @DisplayName("POST /{kbId}/docs/{docId}/reindex")
    class ReindexDocTests {

        @Test
        @DisplayName("正常 → 200 + ApiResponse")
        void reindexDoc_shouldReturn200() throws Exception {
            doNothing().when(kbService).reindexDoc(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(post("/api/v1/kb/kb-001/docs/doc-1/reindex"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(kbService).reindexDoc(eq("kb-001"), eq("doc-1"), eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void reindexDoc_notFound_shouldReturn404() throws Exception {
            doThrow(new BusinessException(404, "文档不存在"))
                    .when(kbService).reindexDoc(eq("kb-001"), eq("doc-999"), eq(USER_ID), any(UserDetails.class));

            mockMvc.perform(post("/api/v1/kb/kb-001/docs/doc-999/reindex"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== POST /{kbId}/search ====================

    @Nested
    @DisplayName("POST /{kbId}/search")
    class SearchKbTests {

        @Test
        @DisplayName("正常检索 → 200 + SearchTestResponse")
        void searchKb_shouldReturn200() throws Exception {
            SearchTestResponse resp = buildSearchResponse(3);
            when(kbService.searchKb(eq("kb-001"), eq("Java面试"), isNull(), eq(5),
                    eq(USER_ID), any(UserDetails.class)))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/v1/kb/kb-001/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"query": "Java面试", "topK": 5}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(3))
                    .andExpect(jsonPath("$.data.latencyMs").value(42));

            verify(kbService).searchKb(eq("kb-001"), eq("Java面试"), isNull(), eq(5),
                    eq(USER_ID), any(UserDetails.class));
        }

        @Test
        @DisplayName("query 为空 → 400 参数校验失败")
        void searchKb_blankQuery_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/kb/kb-001/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"query": ""}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(kbService, never()).searchKb(any(), any(), any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("KB 不存在 → 404")
        void searchKb_notFound_shouldReturn404() throws Exception {
            when(kbService.searchKb(eq("kb-999"), eq("查询"), isNull(), eq(5),
                    eq(USER_ID), any(UserDetails.class)))
                    .thenThrow(new BusinessException(404, "知识库不存在"));

            mockMvc.perform(post("/api/v1/kb/kb-999/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"query": "查询"}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}
