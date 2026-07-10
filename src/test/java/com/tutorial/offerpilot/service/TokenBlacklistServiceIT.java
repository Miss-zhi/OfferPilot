/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.entity.TokenBlacklist;
import com.tutorial.offerpilot.repository.TokenBlacklistRepository;
import com.tutorial.offerpilot.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@DisplayName("TokenBlacklistService 集成测试")
class TokenBlacklistServiceIT extends AbstractServiceIT {

    @Autowired
    private TokenBlacklistService blacklistService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenBlacklistRepository blacklistRepo;

    // ==================== blacklistToken ====================

    @Nested
    @DisplayName("blacklistToken")
    class BlacklistTokenTests {

        @Test
        @DisplayName("添加有效Token → 写入黑名单")
        void blacklistToken_validToken_shouldPersist() {
            String token = jwtTokenProvider.generateToken("bl-test-user");

            blacklistService.blacklistToken(token, "bl-test-user", "LOGOUT");

            runVerify("token-blacklist/blacklist");
        }
    }

    // ==================== isBlacklisted ====================

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklistedTests {

        @Test
        @DisplayName("黑名单中的Token → 返回 true")
        void isBlacklisted_blacklistedToken_shouldReturnTrue() {
            String token = jwtTokenProvider.generateToken("bl-check-user");
            blacklistService.blacklistToken(token, "bl-check-user", "TEST_CHECK");

            boolean result = blacklistService.isBlacklisted(token);

            assertTrue(result);
        }

        @Test
        @DisplayName("非黑名单Token → 返回 false")
        void isBlacklisted_nonBlacklistedToken_shouldReturnFalse() {
            String token = jwtTokenProvider.generateToken("non-bl-user");

            boolean result = blacklistService.isBlacklisted(token);

            assertFalse(result);
        }
    }

    // ==================== cleanExpiredTokens ====================

    @Nested
    @DisplayName("cleanExpiredTokens")
    class CleanExpiredTokensTests {

        @Test
        @DisplayName("清理过期Token → 仅删除过期记录")
        void cleanExpiredTokens_shouldRemoveOnlyExpired() {
            // Arrange: create expired + non-expired entries via JPA
            TokenBlacklist expired1 = new TokenBlacklist();
            expired1.setTokenJti("expired-jti-001");
            expired1.setUserId("cleanup-user");
            expired1.setExpireAt(Instant.now().minus(1, ChronoUnit.HOURS));
            expired1.setBlacklistedAt(Instant.now().minus(2, ChronoUnit.HOURS));
            expired1.setReason("LOGOUT");
            expired1.setCreateBy("test");
            blacklistRepo.saveAndFlush(expired1);

            TokenBlacklist expired2 = new TokenBlacklist();
            expired2.setTokenJti("expired-jti-002");
            expired2.setUserId("cleanup-user");
            expired2.setExpireAt(Instant.now().minus(1, ChronoUnit.DAYS));
            expired2.setBlacklistedAt(Instant.now().minus(2, ChronoUnit.DAYS));
            expired2.setReason("PASSWORD_CHANGE");
            expired2.setCreateBy("test");
            blacklistRepo.saveAndFlush(expired2);

            TokenBlacklist active = new TokenBlacklist();
            active.setTokenJti("active-jti-001");
            active.setUserId("cleanup-user");
            active.setExpireAt(Instant.now().plus(1, ChronoUnit.DAYS));
            active.setBlacklistedAt(Instant.now());
            active.setReason("LOGOUT");
            active.setCreateBy("test");
            blacklistRepo.saveAndFlush(active);

            // Act
            blacklistService.cleanExpiredTokens();

            // Assert: only non-expired entry remains
            assertTrue(blacklistRepo.existsByTokenJti("active-jti-001"));
            assertFalse(blacklistRepo.existsByTokenJti("expired-jti-001"));
            assertFalse(blacklistRepo.existsByTokenJti("expired-jti-002"));
        }
    }
}
