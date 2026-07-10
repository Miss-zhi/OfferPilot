/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("KnowledgeBaseController 集成测试")
class KnowledgeBaseControllerIT extends AbstractControllerIT {

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
                    .andExpect(jsonPath("$.data.kbId").value(startsWith("kb-")))
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
                    .andExpect(jsonPath("$.data.kbId").value(startsWith("kb-")))
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
