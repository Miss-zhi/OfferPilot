/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.auth.AuthResponse;
import com.tutorial.offerpilot.dto.auth.LoginRequest;
import com.tutorial.offerpilot.dto.auth.RegisterRequest;
import com.tutorial.offerpilot.enums.UserRole;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.service.AuthService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Web 层测试")
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private AuthResponse buildAuthResponse() {
        return new AuthResponse("jwt.token.here", "u-test001", "testuser", UserRole.USER);
    }

    // ==================== POST /api/v1/auth/register ====================

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("正常注册 → 201 CREATED + AuthResponse")
        void register_shouldReturn201() throws Exception {
            AuthResponse response = buildAuthResponse();
            when(authService.register(any(RegisterRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "newuser", "password": "pass123456", "email": "a@b.com"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                    .andExpect(jsonPath("$.data.userId").value("u-test001"))
                    .andExpect(jsonPath("$.data.username").value("testuser"))
                    .andExpect(jsonPath("$.data.role").value("USER"));

            verify(authService).register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("用户名为空 → 400 参数校验失败")
        void register_blankUsername_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "", "password": "pass123456", "email": "a@b.com"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("用户名")));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("密码长度不足 6 → 400 参数校验失败")
        void register_shortPassword_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "newuser", "password": "12345", "email": "a@b.com"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("邮箱格式错误 → 400 参数校验失败")
        void register_invalidEmail_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "newuser", "password": "pass123456", "email": "notanemail"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(authService, never()).register(any());
        }

        @Test
        @DisplayName("无邮箱 → 正常注册成功（email 非必填）")
        void register_noEmail_shouldSucceed() throws Exception {
            AuthResponse response = buildAuthResponse();
            when(authService.register(any(RegisterRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "newuser", "password": "pass123456"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.token").value("jwt.token.here"));
        }

        @Test
        @DisplayName("重复用户名 → 业务异常透传")
        void register_duplicate_shouldReturnError() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new BusinessException(409, "用户名已存在"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "dup", "password": "pass123456"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value("用户名已存在"));
        }
    }

    // ==================== POST /api/v1/auth/login ====================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("正常登录 → 200 OK + AuthResponse")
        void login_shouldReturn200() throws Exception {
            AuthResponse response = new AuthResponse("jwt.token.here", "u-test001", "testuser", UserRole.ADMIN);
            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "testuser", "password": "pass123456"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                    .andExpect(jsonPath("$.data.userId").value("u-test001"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));

            verify(authService).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("用户名为空 → 400 参数校验失败")
        void login_blankUsername_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "", "password": "pass123456"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("密码为空 → 400 参数校验失败")
        void login_blankPassword_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "testuser", "password": ""}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("密码错误 → 业务异常透传 401")
        void login_wrongPassword_shouldReturn401() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(401, "用户名或密码错误"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "testuser", "password": "wrongpass"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
