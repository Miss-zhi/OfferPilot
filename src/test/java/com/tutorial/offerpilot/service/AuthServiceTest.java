/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.auth.AuthResponse;
import com.tutorial.offerpilot.dto.auth.LoginRequest;
import com.tutorial.offerpilot.dto.auth.RegisterRequest;
import com.tutorial.offerpilot.entity.AppUser;
import com.tutorial.offerpilot.enums.UserRole;
import com.tutorial.offerpilot.exception.AccountDisabledException;
import com.tutorial.offerpilot.exception.DuplicateUserException;
import com.tutorial.offerpilot.exception.InvalidCredentialsException;
import com.tutorial.offerpilot.repository.AppUserRepository;
import com.tutorial.offerpilot.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private static final String USERNAME = "testuser";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "$2a$encoded";
    private static final String EMAIL = "test@example.com";
    private static final String TOKEN = "jwt.token.here";

    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest();
        loginRequest.setUsername(USERNAME);
        loginRequest.setPassword(RAW_PASSWORD);

        registerRequest = new RegisterRequest();
        registerRequest.setUsername(USERNAME);
        registerRequest.setPassword(RAW_PASSWORD);
        registerRequest.setEmail(EMAIL);
    }

    // ---- 辅助方法 ----

    private AppUser buildUser(String username, String encodedPwd, boolean enabled, UserRole role) {
        AppUser user = new AppUser();
        user.setUserId("u-test001");
        user.setUsername(username);
        user.setPasswordHash(encodedPwd);
        user.setEmail(EMAIL);
        user.setEnabled(enabled);
        user.setRole(role);
        return user;
    }

    // ==================== register ====================

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("正常注册 → 返回 AuthResponse，密码被加密存储")
        void register_newUser_shouldReturnAuthResponse() {
            when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtTokenProvider.generateToken(USERNAME)).thenReturn(TOKEN);

            AuthResponse response = authService.register(registerRequest);

            assertNotNull(response);
            assertEquals(TOKEN, response.getToken());
            assertEquals(USERNAME, response.getUsername());
            assertEquals(UserRole.USER, response.getRole());
            assertNotNull(response.getUserId());
            assertTrue(response.getUserId().startsWith("u-"));

            // 验证保存的实体
            ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
            verify(userRepository).save(captor.capture());
            AppUser saved = captor.getValue();
            assertEquals(USERNAME, saved.getUsername());
            assertEquals(ENCODED_PASSWORD, saved.getPasswordHash());
            assertEquals(EMAIL, saved.getEmail());
            assertEquals(UserRole.USER, saved.getRole());
            assertTrue(saved.getEnabled());
        }

        @Test
        @DisplayName("重复用户名 → 抛 DuplicateUserException")
        void register_duplicateUsername_shouldThrow() {
            when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

            assertThrows(DuplicateUserException.class, () -> authService.register(registerRequest));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("无邮箱 → 正常注册，email 为 null")
        void register_nullEmail_shouldStillSucceed() {
            registerRequest.setEmail(null);
            when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtTokenProvider.generateToken(USERNAME)).thenReturn(TOKEN);

            AuthResponse response = authService.register(registerRequest);

            assertNotNull(response);
            ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
            verify(userRepository).save(captor.capture());
            assertNull(captor.getValue().getEmail());
        }
    }

    // ==================== login ====================

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("正常登录 → 返回 AuthResponse，更新 lastLoginAt")
        void login_validCredentials_shouldReturnAuthResponse() {
            AppUser user = buildUser(USERNAME, ENCODED_PASSWORD, true, UserRole.USER);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtTokenProvider.generateToken(USERNAME)).thenReturn(TOKEN);

            AuthResponse response = authService.login(loginRequest);

            assertNotNull(response);
            assertEquals(TOKEN, response.getToken());
            assertEquals(USERNAME, response.getUsername());
            assertEquals(UserRole.USER, response.getRole());
            assertEquals("u-test001", response.getUserId());

            // 验证 lastLoginAt 被更新并保存
            assertNotNull(user.getLastLoginAt());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("用户名不存在 → 抛 InvalidCredentialsException")
        void login_userNotFound_shouldThrow() {
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("密码错误 → 抛 InvalidCredentialsException")
        void login_wrongPassword_shouldThrow() {
            AppUser user = buildUser(USERNAME, ENCODED_PASSWORD, true, UserRole.USER);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> authService.login(loginRequest));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("账号被禁用 → 抛 AccountDisabledException")
        void login_disabledAccount_shouldThrow() {
            AppUser user = buildUser(USERNAME, ENCODED_PASSWORD, false, UserRole.USER);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            assertThrows(AccountDisabledException.class, () -> authService.login(loginRequest));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN 角色 → 返回正确的 role")
        void login_adminRole_shouldReturnAdminRole() {
            AppUser user = buildUser(USERNAME, ENCODED_PASSWORD, true, UserRole.ADMIN);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtTokenProvider.generateToken(USERNAME)).thenReturn(TOKEN);

            AuthResponse response = authService.login(loginRequest);

            assertEquals(UserRole.ADMIN, response.getRole());
        }
    }
}
