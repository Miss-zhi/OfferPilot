/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.dto.model.*;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.service.UserModelService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserModelController Web 层测试")
class UserModelControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserModelService userModelService;

    @InjectMocks
    private UserModelController controller;

    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        testUser = User.withUsername("testuser").password("password").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()));

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== GET /api/v1/user/models ====================

    @Nested
    @DisplayName("GET /")
    class GetAvailableModelsTests {

        @Test
        @DisplayName("正常获取 → 200 + 模型列表")
        void getAvailableModels_shouldReturn200() throws Exception {
            List<UserModelResponse> models = List.of(
                    UserModelResponse.builder()
                            .configId(1L)
                            .provider("dashscope")
                            .modelName("qwen-max")
                            .isGlobalDefault(true)
                            .isUserDefault(false)
                            .build(),
                    UserModelResponse.builder()
                            .configId(1L)
                            .provider("dashscope")
                            .modelName("qwen-plus")
                            .isGlobalDefault(false)
                            .isUserDefault(false)
                            .build()
            );
            when(userModelService.getAvailableModels("testuser")).thenReturn(models);

            mockMvc.perform(get("/api/v1/user/models"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].modelName").value("qwen-max"))
                    .andExpect(jsonPath("$.data[0].isGlobalDefault").value(true));

            verify(userModelService).getAvailableModels("testuser");
        }
    }

    // ==================== PUT /api/v1/user/models/default ====================

    @Nested
    @DisplayName("PUT /default")
    class SetDefaultModelTests {

        @Test
        @DisplayName("正常设置默认 → 200")
        void setDefaultModel_shouldReturn200() throws Exception {
            doNothing().when(userModelService)
                    .setDefaultModel(eq("testuser"), any(SetDefaultModelRequest.class));

            mockMvc.perform(put("/api/v1/user/models/default")
                            
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"modelConfigId\":1,\"modelName\":\"qwen-max\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(userModelService).setDefaultModel(eq("testuser"), any(SetDefaultModelRequest.class));
        }

        @Test
        @DisplayName("modelConfigId 缺失 → 400 参数校验失败")
        void setDefaultModel_missingConfigId_shouldReturn400() throws Exception {
            mockMvc.perform(put("/api/v1/user/models/default")
                            
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"modelName\":\"qwen-max\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(userModelService, never()).setDefaultModel(anyString(), any());
        }

        @Test
        @DisplayName("模型已禁用 → 业务异常透传")
        void setDefaultModel_disabled_shouldReturnError() throws Exception {
            doThrow(new BusinessException(400, "该模型已被禁用"))
                    .when(userModelService).setDefaultModel(eq("testuser"), any(SetDefaultModelRequest.class));

            mockMvc.perform(put("/api/v1/user/models/default")
                            
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"modelConfigId\":1,\"modelName\":\"qwen-max\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("该模型已被禁用"));
        }
    }

    // ==================== POST /api/v1/user/models/private ====================

    @Nested
    @DisplayName("POST /private")
    class CreatePrivateModelTests {

        @Test
        @DisplayName("正常创建私有模型 → 200 + 配置结果")
        void createPrivateModel_shouldReturn200() throws Exception {
            ModelConfigResponse resp = ModelConfigResponse.builder()
                    .id(99L)
                    .provider("dashscope")
                    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                    .apiKey("sk-***test")
                    .apiFormat("openai")
                    .authHeaderType("bearer")
                    .modelListUrl("https://models.example.com")
                    .defaultModelName("qwen-max")
                    .isEnabled(true)
                    .isPrivate(true)
                    .modelNames(List.of("qwen-max"))
                    .build();
            when(userModelService.createPrivateModel(eq("testuser"), any(PrivateModelRequest.class)))
                    .thenReturn(resp);

            mockMvc.perform(post("/api/v1/user/models/private")
                            
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"dashscope\",\"apiKey\":\"sk-test\",\"modelName\":\"qwen-max\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.isPrivate").value(true));

            verify(userModelService).createPrivateModel(eq("testuser"), any(PrivateModelRequest.class));
        }

        @Test
        @DisplayName("Provider 为空 → 400 参数校验失败")
        void createPrivateModel_blankProvider_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/user/models/private")
                            
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"\",\"apiKey\":\"sk-test\",\"modelName\":\"qwen-max\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(userModelService, never()).createPrivateModel(anyString(), any());
        }

        @Test
        @DisplayName("不支持的 Provider → 业务异常透传")
        void createPrivateModel_unknownProvider_shouldReturnError() throws Exception {
            when(userModelService.createPrivateModel(eq("testuser"), any(PrivateModelRequest.class)))
                    .thenThrow(new BusinessException(400, "不支持的 Provider: unknown"));

            mockMvc.perform(post("/api/v1/user/models/private")
                            
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"provider\":\"unknown\",\"apiKey\":\"sk-test\",\"modelName\":\"test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("不支持的 Provider")));
        }
    }
}
