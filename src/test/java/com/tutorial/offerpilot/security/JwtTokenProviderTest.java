/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtTokenProvider 单元测试")
class JwtTokenProviderTest {

    private static final String SECRET = "this-is-a-test-secret-key-that-is-long-enough-for-hmac-sha256";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 小时
    private static final String USERNAME = "testuser";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    // ==================== generateToken ====================

    @Nested
    @DisplayName("generateToken")
    class GenerateTokenTests {

        @Test
        @DisplayName("正常生成 → 返回三段式 JWT 且 username 可解析")
        void generateToken_shouldReturnValidJwt() {
            String token = jwtTokenProvider.generateToken(USERNAME);

            assertNotNull(token);
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "JWT 应为三段式 header.payload.signature");

            String extracted = jwtTokenProvider.getUsernameFromToken(token);
            assertEquals(USERNAME, extracted);
        }
    }

    // ==================== getUsernameFromToken ====================

    @Nested
    @DisplayName("getUsernameFromToken")
    class GetUsernameFromTokenTests {

        @Test
        @DisplayName("有效 token → 返回正确 username")
        void getUsernameFromToken_validToken_shouldReturnUsername() {
            String token = jwtTokenProvider.generateToken("alice");
            assertEquals("alice", jwtTokenProvider.getUsernameFromToken(token));
        }

        @Test
        @DisplayName("篡改 token → 抛异常")
        void getUsernameFromToken_tamperedToken_shouldThrow() {
            String token = jwtTokenProvider.generateToken(USERNAME);
            String tampered = token.substring(0, token.length() - 1) + "X";

            assertThrows(JwtException.class, () -> jwtTokenProvider.getUsernameFromToken(tampered));
        }
    }

    // ==================== getJtiFromToken ====================

    @Nested
    @DisplayName("getJtiFromToken")
    class GetJtiFromTokenTests {

        @Test
        @DisplayName("token 包含 JTI → 返回非 null UUID")
        void getJtiFromToken_shouldReturnJti() {
            String token = jwtTokenProvider.generateToken(USERNAME);
            // generateToken 使用 UUID.randomUUID() 设置 JTI
            assertNotNull(jwtTokenProvider.getJtiFromToken(token));
        }
    }

    // ==================== validateToken ====================

    @Nested
    @DisplayName("validateToken")
    class ValidateTokenTests {

        @Test
        @DisplayName("有效 token → true")
        void validateToken_validToken_shouldReturnTrue() {
            String token = jwtTokenProvider.generateToken(USERNAME);
            assertTrue(jwtTokenProvider.validateToken(token));
        }

        @Test
        @DisplayName("篡改 token → false")
        void validateToken_tamperedToken_shouldReturnFalse() {
            String token = jwtTokenProvider.generateToken(USERNAME);
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "tampered";
            assertFalse(jwtTokenProvider.validateToken(tampered));
        }

        @Test
        @DisplayName("过期 token → false")
        void validateToken_expiredToken_shouldReturnFalse() throws InterruptedException {
            JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1); // 1ms 过期
            String expiredToken = shortLived.generateToken(USERNAME);
            Thread.sleep(10); // 确保过期

            assertFalse(shortLived.validateToken(expiredToken));
        }

        @Test
        @DisplayName("null → false")
        void validateToken_null_shouldReturnFalse() {
            assertFalse(jwtTokenProvider.validateToken(null));
        }

        @Test
        @DisplayName("空字符串 → false")
        void validateToken_emptyString_shouldReturnFalse() {
            assertFalse(jwtTokenProvider.validateToken(""));
        }

        @Test
        @DisplayName("格式错误 → false")
        void validateToken_malformed_shouldReturnFalse() {
            assertFalse(jwtTokenProvider.validateToken("not.a.jwt"));
        }
    }

    // ==================== getExpirationFromToken ====================

    @Nested
    @DisplayName("getExpirationFromToken")
    class GetExpirationFromTokenTests {

        @Test
        @DisplayName("提取过期时间 → 在合理范围内")
        void getExpirationFromToken_shouldReturnCorrectExpiration() {
            long before = System.currentTimeMillis() + EXPIRATION_MS;
            String token = jwtTokenProvider.generateToken(USERNAME);
            long expiration = jwtTokenProvider.getExpirationFromToken(token);
            long after = System.currentTimeMillis() + EXPIRATION_MS;

            assertTrue(expiration >= before - 1000, "过期时间不应早于生成时 + expirationMs");
            assertTrue(expiration <= after + 1000, "过期时间不应晚于生成时 + expirationMs");
        }
    }
}
