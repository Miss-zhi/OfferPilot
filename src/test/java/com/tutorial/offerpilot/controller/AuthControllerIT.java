/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AuthController 集成测试")
class AuthControllerIT extends AbstractControllerIT {

    // ==================== POST /api/v1/auth/register ====================

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("正常注册 → 201 + Token + userId")
        void register_withValidData_shouldReturn201AndToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "newuser", "password": "pass123456", "email": "new@test.com"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andExpect(jsonPath("$.data.userId").isNotEmpty())
                    .andExpect(jsonPath("$.data.username").value("newuser"))
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("无邮箱注册 → 成功（email 非必填）")
        void register_withoutEmail_shouldSucceed() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "noemail", "password": "pass123456"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username").value("noemail"));
        }

        @Test
        @DisplayName("重复用户名 → 409 Conflict")
        void register_duplicateUsername_shouldReturn409() throws Exception {
            // First registration
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "dupuser", "password": "pass123456"}"""))
                    .andExpect(status().isCreated());

            // Duplicate registration
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "dupuser", "password": "pass123456"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409));
        }

        @Test
        @DisplayName("用户名为空 → 400 参数校验失败")
        void register_blankUsername_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "", "password": "pass123456"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("密码长度不足6 → 400 参数校验失败")
        void register_shortPassword_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "testuser", "password": "12345"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("邮箱格式无效 → 400 参数校验失败")
        void register_invalidEmail_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "testuser", "password": "pass123456", "email": "notanemail"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== POST /api/v1/auth/login ====================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("正确凭据 → 200 + Token")
        void login_withValidCredentials_shouldReturn200AndToken() throws Exception {
            // Register a user first
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "loginuser", "password": "pass123456", "email": "login@test.com"}"""))
                    .andExpect(status().isCreated());

            // Login
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "loginuser", "password": "pass123456"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andExpect(jsonPath("$.data.username").value("loginuser"));
        }

        @Test
        @DisplayName("错误密码 → 401 Unauthorized")
        void login_wrongPassword_shouldReturn401() throws Exception {
            // Register a user first
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "wrongpwd", "password": "pass123456"}"""))
                    .andExpect(status().isCreated());

            // Wrong password
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "wrongpwd", "password": "wrongpass"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("不存在用户 → 401 Unauthorized")
        void login_nonExistentUser_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "noone", "password": "pass123456"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
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
        }
    }
}
