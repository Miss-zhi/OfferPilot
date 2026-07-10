/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.model.*;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.service.ModelConfigService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelConfigController Web 层测试")
class ModelConfigControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ModelConfigService modelConfigService;

    @InjectMocks
    private ModelConfigController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private ModelConfigResponse buildConfigResponse() {
        return ModelConfigResponse.builder()
                .id(1L)
                .provider("dashscope")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey("sk-***test")
                .apiFormat("openai")
                .authHeaderType("bearer")
                .modelListUrl("https://models.example.com")
                .defaultModelName("qwen-max")
                .isEnabled(true)
                .isGlobalDefault(false)
                .isPrivate(false)
                .modelNames(List.of("qwen-max", "qwen-plus"))
                .build();
    }

    // ==================== GET /api/v1/admin/models ====================

    @Nested
    @DisplayName("GET /")
    class ListConfigsTests {

        @Test
        @DisplayName("正常列表 → 200 + 配置数组")
        void listConfigs_shouldReturn200() throws Exception {
            ModelConfigResponse resp = buildConfigResponse();
            when(modelConfigService.listConfigs()).thenReturn(List.of(resp));

            mockMvc.perform(get("/api/v1/admin/models"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].provider").value("dashscope"))
                    .andExpect(jsonPath("$.data[0].modelNames[0]").value("qwen-max"));

            verify(modelConfigService).listConfigs();
        }
    }

    // ==================== POST /api/v1/admin/models ====================

    @Nested
    @DisplayName("POST /")
    class CreateConfigTests {

        @Test
        @DisplayName("正常新增 → 200 + 创建结果")
        void createConfig_shouldReturn200() throws Exception {
            ModelConfigResponse resp = buildConfigResponse();
            when(modelConfigService.createConfig(any(CreateModelConfigRequest.class))).thenReturn(resp);

            mockMvc.perform(post("/api/v1/admin/models")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"dashscope\",\"apiKey\":\"sk-test\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.provider").value("dashscope"));

            verify(modelConfigService).createConfig(any(CreateModelConfigRequest.class));
        }

        @Test
        @DisplayName("Provider 为空 → 400 参数校验失败")
        void createConfig_blankProvider_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/admin/models")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"\",\"apiKey\":\"sk-test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(modelConfigService, never()).createConfig(any());
        }

        @Test
        @DisplayName("API Key 为空 → 400 参数校验失败")
        void createConfig_blankApiKey_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/admin/models")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"dashscope\",\"apiKey\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(modelConfigService, never()).createConfig(any());
        }
    }

    // ==================== PUT /api/v1/admin/models/{id} ====================

    @Nested
    @DisplayName("PUT /{id}")
    class UpdateConfigTests {

        @Test
        @DisplayName("正常更新 → 200 + 更新结果")
        void updateConfig_shouldReturn200() throws Exception {
            ModelConfigResponse resp = buildConfigResponse();
            when(modelConfigService.updateConfig(eq(1L), any(UpdateModelConfigRequest.class)))
                    .thenReturn(resp);

            mockMvc.perform(put("/api/v1/admin/models/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"defaultModelName\":\"qwen-max\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(modelConfigService).updateConfig(eq(1L), any(UpdateModelConfigRequest.class));
        }

        @Test
        @DisplayName("配置不存在 → 业务异常透传")
        void updateConfig_notFound_shouldReturnError() throws Exception {
            when(modelConfigService.updateConfig(eq(999L), any(UpdateModelConfigRequest.class)))
                    .thenThrow(new BusinessException(404, "模型配置不存在: 999"));

            mockMvc.perform(put("/api/v1/admin/models/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"defaultModelName\":\"test\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== DELETE /api/v1/admin/models/{id} ====================

    @Nested
    @DisplayName("DELETE /{id}")
    class DeleteConfigTests {

        @Test
        @DisplayName("正常删除 → 200")
        void deleteConfig_shouldReturn200() throws Exception {
            doNothing().when(modelConfigService).deleteConfig(1L);

            mockMvc.perform(delete("/api/v1/admin/models/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(modelConfigService).deleteConfig(1L);
        }

        @Test
        @DisplayName("有用户引用时删除 → 409 冲突")
        void deleteConfig_withUsers_shouldReturn409() throws Exception {
            doThrow(new BusinessException(409, "有 3 位用户正在使用此模型配置，无法删除"))
                    .when(modelConfigService).deleteConfig(1L);

            mockMvc.perform(delete("/api/v1/admin/models/1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("无法删除")));
        }
    }

    // ==================== POST /api/v1/admin/models/{id}/refresh-models ====================

    @Nested
    @DisplayName("POST /{id}/refresh-models")
    class RefreshModelsTests {

        @Test
        @DisplayName("正常刷新 → 200 + 模型名列表")
        void refreshModels_shouldReturn200() throws Exception {
            when(modelConfigService.refreshModels(1L))
                    .thenReturn(List.of("qwen-max", "qwen-plus", "qwen-turbo"));

            mockMvc.perform(post("/api/v1/admin/models/1/refresh-models"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0]").value("qwen-max"))
                    .andExpect(jsonPath("$.data[2]").value("qwen-turbo"));

            verify(modelConfigService).refreshModels(1L);
        }
    }

    // ==================== PUT /api/v1/admin/models/{id}/set-global-default ====================

    @Nested
    @DisplayName("PUT /{id}/set-global-default")
    class SetGlobalDefaultTests {

        @Test
        @DisplayName("正常设置 → 200 + 配置结果")
        void setGlobalDefault_shouldReturn200() throws Exception {
            ModelConfigResponse resp = buildConfigResponse();
            resp.setIsGlobalDefault(true);
            when(modelConfigService.setGlobalDefault(eq(1L), eq("qwen-max"))).thenReturn(resp);

            mockMvc.perform(put("/api/v1/admin/models/1/set-global-default")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"modelName\":\"qwen-max\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.isGlobalDefault").value(true));

            verify(modelConfigService).setGlobalDefault(1L, "qwen-max");
        }

        @Test
        @DisplayName("modelName 为空 → 400 参数校验失败")
        void setGlobalDefault_blankModelName_shouldReturn400() throws Exception {
            mockMvc.perform(put("/api/v1/admin/models/1/set-global-default")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"modelName\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(modelConfigService, never()).setGlobalDefault(anyLong(), anyString());
        }
    }

    // ==================== GET /api/v1/admin/models/provider-presets ====================

    @Nested
    @DisplayName("GET /provider-presets")
    class ListProviderPresetsTests {

        @Test
        @DisplayName("正常获取预设 → 200 + 8 个 Provider")
        void listProviderPresets_shouldReturn200() throws Exception {
            when(modelConfigService.listProviderPresets()).thenReturn(
                    List.of(
                            ProviderPresetResponse.builder().provider("dashscope").displayName("阿里百炼").build(),
                            ProviderPresetResponse.builder().provider("openai").displayName("OpenAI").build()
                    ));

            mockMvc.perform(get("/api/v1/admin/models/provider-presets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].provider").value("dashscope"));

            verify(modelConfigService).listProviderPresets();
        }
    }
}
