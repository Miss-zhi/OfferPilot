/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import com.tutorial.offerpilot.service.VectorSearchService;
import com.tutorial.offerpilot.service.ingestion.DocumentIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.hamcrest.Matchers;

@DisplayName("KnowledgeBaseController 集成测试")
class KnowledgeBaseControllerIT extends AbstractControllerIT {

    @MockBean
    private DocumentIngestionService ingestionService;

    @MockBean
    private VectorSearchService vectorSearchService;

    // ==================== POST /api/v1/kb ====================

    @Nested
    @DisplayName("POST /api/v1/kb")
    class CreateKbTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/kb")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "测试库", "category": "技术"}"""))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin 创建 → 201 + KbResponse（PUBLIC 可见性）")
        void admin_shouldCreatePublicKb() throws Exception {
            String token = registerAdminAndGetToken("kbcr8admin");

            mockMvc.perform(post("/api/v1/kb")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "公共知识库", "category": "产品"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.kbId").isNotEmpty())
                    .andExpect(jsonPath("$.data.kbId").value(Matchers.startsWith("kb-")))
                    .andExpect(jsonPath("$.data.name").value("公共知识库"))
                    .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("普通用户创建 → 201 + KbResponse（PRIVATE 可见性）")
        void normalUser_shouldCreatePrivateKb() throws Exception {
            String token = registerUserAndGetToken("kbnormal");

            mockMvc.perform(post("/api/v1/kb")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "我的私有库", "category": "技术"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.kbId").isNotEmpty())
                    .andExpect(jsonPath("$.data.kbId").value(Matchers.startsWith("kb-")))
                    .andExpect(jsonPath("$.data.name").value("我的私有库"))
                    .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }
    }

    // ==================== GET /api/v1/kb ====================

    @Nested
    @DisplayName("GET /api/v1/kb")
    class ListKbTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/kb"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin → 200 + KB 列表")
        void admin_shouldReturn200AndKbList() throws Exception {
            String token = registerAdminAndGetToken("kblistadm");

            mockMvc.perform(get("/api/v1/kb")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("普通用户 → 200 + KB 列表")
        void normalUser_shouldReturn200AndKbList() throws Exception {
            String token = registerUserAndGetToken("kblistuser");

            mockMvc.perform(get("/api/v1/kb")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ==================== DELETE /api/v1/kb/{kbId} ====================

    @Nested
    @DisplayName("DELETE /api/v1/kb/{kbId}")
    class DeleteKbTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(delete("/api/v1/kb/kb-001"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin → 204 No Content")
        void admin_shouldReturn204() throws Exception {
            String token = registerAdminAndGetToken("kbdeladmin");

            String createResult = mockMvc.perform(post("/api/v1/kb")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "待删除库", "category": "测试"}"""))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String kbId = extractKbId(createResult);

            mockMvc.perform(delete("/api/v1/kb/" + kbId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("普通用户删除自己的 KB → 204 No Content")
        void normalUser_deleteOwnKb_shouldReturn204() throws Exception {
            String token = registerUserAndGetToken("kbdelown");

            String createResult = mockMvc.perform(post("/api/v1/kb")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "我的待删库", "category": "测试"}"""))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String kbId = extractKbId(createResult);

            mockMvc.perform(delete("/api/v1/kb/" + kbId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("普通用户删除他人的 KB → 403 Forbidden")
        void normalUser_deleteOthersKb_shouldReturn403() throws Exception {
            String adminToken = registerAdminAndGetToken("kbadmin2");
            String userToken = registerUserAndGetToken("kbusernotowner");

            String createResult = mockMvc.perform(post("/api/v1/kb")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Admin的公共库", "category": "测试"}"""))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String kbId = extractKbId(createResult);

            mockMvc.perform(delete("/api/v1/kb/" + kbId)
                            .header("Authorization", bearer(userToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("无权删除此知识库"));
        }
    }

    // ==================== GET /{kbId}/stats ====================

    @Nested
    @DisplayName("GET /{kbId}/stats")
    class GetKbStatsIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/kb/kb-001/stats"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin → 200 + 统计信息")
        void admin_shouldReturn200() throws Exception {
            String token = registerAdminAndGetToken("kbstatadm");
            String kbId = createTestKb(token, "统计测试库");

            mockMvc.perform(get("/api/v1/kb/" + kbId + "/stats")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.kbId").value(kbId))
                    .andExpect(jsonPath("$.data.documentCount").value(0))
                    .andExpect(jsonPath("$.data.chunkCount").value(0))
                    .andExpect(jsonPath("$.data.activeDocuments").value(0))
                    .andExpect(jsonPath("$.data.failedDocuments").value(0));
        }

        @Test
        @DisplayName("普通用户访问他人 KB → 403")
        void normalUser_accessOthersKb_shouldReturn403() throws Exception {
            String adminToken = registerAdminAndGetToken("kbstatadm2");
            String userToken = registerUserAndGetToken("kbstatuser");
            String kbId = createTestKb(adminToken, "Admin统计库");

            mockMvc.perform(get("/api/v1/kb/" + kbId + "/stats")
                            .header("Authorization", bearer(userToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    // ==================== GET /{kbId}/docs ====================

    @Nested
    @DisplayName("GET /{kbId}/docs")
    class ListDocsIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/kb/kb-001/docs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin → 200 + 文档列表（空）")
        void admin_shouldReturn200() throws Exception {
            String token = registerAdminAndGetToken("kbdocsadm");
            String kbId = createTestKb(token, "文档列表测试库");

            mockMvc.perform(get("/api/v1/kb/" + kbId + "/docs")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("普通用户访问他人 KB → 403")
        void normalUser_accessOthersKb_shouldReturn403() throws Exception {
            String adminToken = registerAdminAndGetToken("kbdocsadm2");
            String userToken = registerUserAndGetToken("kbdocsuser");
            String kbId = createTestKb(adminToken, "Admin文档库");

            mockMvc.perform(get("/api/v1/kb/" + kbId + "/docs")
                            .header("Authorization", bearer(userToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    // ==================== POST /{kbId}/docs (upload) ====================

    @Nested
    @DisplayName("POST /{kbId}/docs")
    class UploadDocIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(multipart("/api/v1/kb/kb-001/docs")
                            .file("file", "content".getBytes()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin 上传文档 → 201 + DocResponse")
        void admin_shouldUploadDoc() throws Exception {
            String token = registerAdminAndGetToken("kbupladm");
            String kbId = createTestKb(token, "上传测试库");
            doNothing().when(ingestionService).ingestDocument(anyString());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "test content".getBytes());

            mockMvc.perform(multipart("/api/v1/kb/" + kbId + "/docs")
                            .file(file)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.docId").value(Matchers.startsWith("doc-")))
                    .andExpect(jsonPath("$.data.kbId").value(kbId))
                    .andExpect(jsonPath("$.data.fileName").value("test.pdf"))
                    .andExpect(jsonPath("$.data.status").value("UPLOADED"));
        }

        @Test
        @DisplayName("普通用户上传到自己的 KB → 201")
        void normalUser_uploadToOwnKb_shouldReturn201() throws Exception {
            String token = registerUserAndGetToken("kbupluser");
            String kbId = createTestKb(token, "我的上传库");
            doNothing().when(ingestionService).ingestDocument(anyString());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "notes.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/kb/" + kbId + "/docs")
                            .file(file)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.fileName").value("notes.txt"))
                    .andExpect(jsonPath("$.data.fileType").value("txt"));
        }
    }

    // ==================== GET /{kbId}/docs/{docId} ====================

    @Nested
    @DisplayName("GET /{kbId}/docs/{docId}")
    class GetDocDetailIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/kb/kb-001/docs/doc-001"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void docNotFound_shouldReturn404() throws Exception {
            String token = registerAdminAndGetToken("kbdocdetail");
            String kbId = createTestKb(token, "详情测试库");

            mockMvc.perform(get("/api/v1/kb/" + kbId + "/docs/doc-999")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== DELETE /{kbId}/docs/{docId} ====================

    @Nested
    @DisplayName("DELETE /{kbId}/docs/{docId}")
    class DeleteDocIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(delete("/api/v1/kb/kb-001/docs/doc-001"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void docNotFound_shouldReturn404() throws Exception {
            String token = registerAdminAndGetToken("kbdel2adm");
            String kbId = createTestKb(token, "删除测试库");

            mockMvc.perform(delete("/api/v1/kb/" + kbId + "/docs/doc-999")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== GET /{kbId}/docs/{docId}/progress ====================

    @Nested
    @DisplayName("GET /{kbId}/docs/{docId}/progress")
    class GetDocProgressIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/kb/kb-001/docs/doc-001/progress"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void docNotFound_shouldReturn404() throws Exception {
            String token = registerAdminAndGetToken("kbprogadm");
            String kbId = createTestKb(token, "进度测试库");

            mockMvc.perform(get("/api/v1/kb/" + kbId + "/docs/doc-999/progress")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== POST /{kbId}/docs/{docId}/reindex ====================

    @Nested
    @DisplayName("POST /{kbId}/docs/{docId}/reindex")
    class ReindexDocIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/kb/kb-001/docs/doc-001/reindex"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("文档不存在 → 404")
        void docNotFound_shouldReturn404() throws Exception {
            String token = registerAdminAndGetToken("kbreidxadm");
            String kbId = createTestKb(token, "重建索引测试库");

            mockMvc.perform(post("/api/v1/kb/" + kbId + "/docs/doc-999/reindex")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== POST /{kbId}/search ====================

    @Nested
    @DisplayName("POST /{kbId}/search")
    class SearchKbIT {

        @Test
        @DisplayName("未认证 → 401")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/kb/kb-001/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\": \"测试\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("query 为空 → 400")
        void blankQuery_shouldReturn400() throws Exception {
            String token = registerAdminAndGetToken("kbsrchadm");
            String kbId = createTestKb(token, "搜索测试库");

            mockMvc.perform(post("/api/v1/kb/" + kbId + "/search")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\": \"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 辅助方法 ====================

    /** 创建测试知识库并返回 kbId */
    private String createTestKb(String token, String name) throws Exception {
        String result = mockMvc.perform(post("/api/v1/kb")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"category\": \"测试\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractKbId(result);
    }

    private String extractKbId(String json) {
        int idx = json.indexOf("\"kbId\":\"");
        if (idx < 0) {
            throw new IllegalStateException("Cannot find kbId in response: " + json);
        }
        int start = idx + 8;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
