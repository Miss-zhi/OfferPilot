/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.TokenBlacklist;
import com.tutorial.offerpilot.repository.TokenBlacklistRepository;
import com.tutorial.offerpilot.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService 单元测试")
class TokenBlacklistServiceTest {

    @Mock
    private TokenBlacklistRepository blacklistRepo;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    private static final String TOKEN = "header.payload.signature";
    private static final String USER_ID = "u-001";
    private static final String JTI = "jti-12345";

    @Nested
    @DisplayName("blacklistToken")
    class BlacklistTokenTests {

        @Test
        @DisplayName("正常拉黑 → 保存 TokenBlacklist 实体")
        void blacklistToken_shouldSaveEntity() {
            when(jwtTokenProvider.getJtiFromToken(TOKEN)).thenReturn(JTI);
            when(jwtTokenProvider.getExpirationFromToken(TOKEN)).thenReturn(1700000000000L);

            tokenBlacklistService.blacklistToken(TOKEN, USER_ID, "LOGOUT");

            ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
            verify(blacklistRepo).save(captor.capture());
            TokenBlacklist saved = captor.getValue();
            assertEquals(JTI, saved.getTokenJti());
            assertEquals(USER_ID, saved.getUserId());
            assertEquals("LOGOUT", saved.getReason());
            assertNotNull(saved.getBlacklistedAt());
        }
    }

    @Nested
    @DisplayName("isBlacklisted")
    class IsBlacklistedTests {

        @Test
        @DisplayName("已拉黑 → 返回 true")
        void isBlacklisted_true_shouldReturnTrue() {
            when(jwtTokenProvider.getJtiFromToken(TOKEN)).thenReturn(JTI);
            when(blacklistRepo.existsByTokenJti(JTI)).thenReturn(true);

            assertTrue(tokenBlacklistService.isBlacklisted(TOKEN));
        }

        @Test
        @DisplayName("未拉黑 → 返回 false")
        void isBlacklisted_false_shouldReturnFalse() {
            when(jwtTokenProvider.getJtiFromToken(TOKEN)).thenReturn(JTI);
            when(blacklistRepo.existsByTokenJti(JTI)).thenReturn(false);

            assertFalse(tokenBlacklistService.isBlacklisted(TOKEN));
        }
    }

    @Nested
    @DisplayName("cleanExpiredTokens")
    class CleanExpiredTokensTests {

        @Test
        @DisplayName("清理过期 token → 调用 Repository 删除")
        void cleanExpiredTokens_shouldDelete() {
            tokenBlacklistService.cleanExpiredTokens();

            verify(blacklistRepo).deleteByExpireAtBefore(any());
        }
    }
}
