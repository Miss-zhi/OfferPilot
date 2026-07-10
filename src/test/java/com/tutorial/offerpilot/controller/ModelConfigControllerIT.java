/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import com.tutorial.offerpilot.service.ModelListFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ModelConfigController 集成测试")
class ModelConfigControllerIT extends AbstractControllerIT {

    @MockBean
    private ModelListFetcher modelListFetcher;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = registerAdminAndGetToken("modeladmin");
        userToken = registerUserAndGetToken("modeluser");

        // Mock model list fetcher to return static model names
        when(modelListFetcher.fetchModelNames(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of("qwen-max", "qwen-plus", "qwen-turbo"));
    }

    // ==================== Permission Tests ====================

    @Nested
    @DisplayName("权限验证")
    class PermissionTests {

        @Test
        @DisplayName("非 ADMIN 用户访问管理接口 → 403 Forbidden")
        void nonAdmin_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/models")
                            .header("Authorization", bearer(userToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
        }

        @Test
        @DisplayName("未认证访问管理接口 → 401 Unauthorized")
        void unauthenticated_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/models"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    // ==================== CRUD Flow Tests ====================

    @Nested
    @DisplayName("完整 CRUD 流程")
    class CrudFlowTests {

        private Long createdConfigId;

        @Test
        @DisplayName("完整流程：新增 → 列表 → 更新 → 设为全局默认 → 刷新 → 删除")
        void fullCrudFlow() throws Exception {
            // 1. 新增模型配置
            String createResp = mockMvc.perform(post("/api/v1/admin/models")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"dashscope\",\"apiKey\":\"sk-it-test-key\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.provider").value("dashscope"))
                    .andExpect(jsonPath("$.data.isEnabled").value(true))
                    .andExpect(jsonPath("$.data.modelNames").isArray())
                    .andReturn().getResponse().getContentAsString();

            createdConfigId = extractId(createResp);

            // 2. 列表应包含新创建的配置
            mockMvc.perform(get("/api/v1/admin/models")
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].provider").value("dashscope"));

            // 3. 更新配置（修改默认模型名称）
            mockMvc.perform(put("/api/v1/admin/models/" + createdConfigId)
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"defaultModelName\":\"qwen-max\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.defaultModelName").value("qwen-max"));

            // 4. 设为全局默认
            mockMvc.perform(put("/api/v1/admin/models/" + createdConfigId + "/set-global-default")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"modelName\":\"qwen-max\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.isGlobalDefault").value(true));

            // 5. 重新拉取模型列表
            mockMvc.perform(post("/api/v1/admin/models/" + createdConfigId + "/refresh-models")
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0]").value("qwen-max"));

            // 6. 获取 Provider 预设列表（需认证但无需 ADMIN）
            mockMvc.perform(get("/api/v1/admin/models/provider-presets")
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(8));

            // 7. 删除配置
            mockMvc.perform(delete("/api/v1/admin/models/" + createdConfigId)
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // 8. 删除后列表为空
            mockMvc.perform(get("/api/v1/admin/models")
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("参数校验")
    class ValidationTests {

        @Test
        @DisplayName("Provider 为空 → 400")
        void create_withBlankProvider_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/admin/models")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"\",\"apiKey\":\"sk-test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("未知 Provider → 400 业务异常")
        void create_withUnknownProvider_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/admin/models")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"unknown\",\"apiKey\":\"sk-test\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    private Long extractId(String json) throws Exception {
        // Simple extraction: find "id":N in JSON
        int idx = json.indexOf("\"id\":");
        if (idx < 0) return null;
        int start = idx + 5;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
