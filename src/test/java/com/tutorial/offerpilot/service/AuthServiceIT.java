/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.dto.auth.AuthResponse;
import com.tutorial.offerpilot.dto.auth.LoginRequest;
import com.tutorial.offerpilot.dto.auth.RegisterRequest;
import com.tutorial.offerpilot.exception.DuplicateUserException;
import com.tutorial.offerpilot.exception.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("AuthService 集成测试")
class AuthServiceIT extends AbstractServiceIT {

    @Autowired
    private AuthService authService;

    // ==================== register ====================

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("新用户注册 → 返回 Token + 写入数据库")
        void register_withValidData_shouldReturnTokenAndPersist() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("test_register_user");
            req.setPassword("password123");
            req.setEmail("test@example.com");

            AuthResponse result = authService.register(req);

            assertNotNull(result.getToken());
            assertEquals("test_register_user", result.getUsername());
            assertNotNull(result.getUserId());
            runVerify("auth-service/register");
        }

        @Test
        @DisplayName("重复用户名 → 抛出 DuplicateUserException")
        void register_duplicateUsername_shouldThrowException() {
            runSetup("auth-service/register");

            RegisterRequest req = new RegisterRequest();
            req.setUsername("existing_user");
            req.setPassword("password123");

            assertThrows(DuplicateUserException.class, () -> authService.register(req));
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("login")
    class LoginTests {

        /**
         * Programmatically registers a test user, then tests login with the same credentials.
         */
        @Test
        @DisplayName("正确凭据 → 返回 Token + 更新 lastLoginAt")
        void login_withValidCredentials_shouldReturnToken() {
            // Arrange: register a user first
            RegisterRequest regReq = new RegisterRequest();
            regReq.setUsername("login_test_user");
            regReq.setPassword("password123");
            regReq.setEmail("login@test.com");
            authService.register(regReq);

            // Act: login
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsername("login_test_user");
            loginReq.setPassword("password123");

            AuthResponse result = authService.login(loginReq);

            // Assert
            assertNotNull(result.getToken());
            assertEquals("login_test_user", result.getUsername());
            runVerify("auth-service/login");
        }

        @Test
        @DisplayName("错误密码 → 抛出 InvalidCredentialsException")
        void login_wrongPassword_shouldThrowException() {
            // Arrange
            RegisterRequest regReq = new RegisterRequest();
            regReq.setUsername("wrong_pwd_user");
            regReq.setPassword("password123");
            authService.register(regReq);

            // Act & Assert
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsername("wrong_pwd_user");
            loginReq.setPassword("wrong_password");

            assertThrows(InvalidCredentialsException.class, () -> authService.login(loginReq));
        }

        @Test
        @DisplayName("不存在的用户 → 抛出 InvalidCredentialsException")
        void login_nonExistingUser_shouldThrowException() {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsername("nonexistent_user");
            loginReq.setPassword("password123");

            assertThrows(InvalidCredentialsException.class, () -> authService.login(loginReq));
        }
    }
}
