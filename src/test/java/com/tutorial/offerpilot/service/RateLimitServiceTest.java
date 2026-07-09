/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService 单元测试")
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RateLimitService rateLimitService;

    private static final String USER_ID = "u-001";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ==================== tryAcquireDialogue ====================

    @Nested
    @DisplayName("tryAcquireDialogue")
    class DialogueTests {

        @Test
        @DisplayName("首次请求 → 设置过期时间，返回 true")
        void tryAcquireDialogue_firstCall_shouldSetExpireAndReturnTrue() {
            when(valueOps.increment("ratelimit:chat:" + USER_ID)).thenReturn(1L);

            assertTrue(rateLimitService.tryAcquireDialogue(USER_ID));
            verify(redis).expire("ratelimit:chat:" + USER_ID, 60, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("未超限 → 返回 true")
        void tryAcquireDialogue_withinLimit_shouldReturnTrue() {
            when(valueOps.increment("ratelimit:chat:" + USER_ID)).thenReturn(15L);

            assertTrue(rateLimitService.tryAcquireDialogue(USER_ID));
            verify(redis, never()).expire(anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("已达上限(30) → 返回 true（边界）")
        void tryAcquireDialogue_atLimit_shouldReturnTrue() {
            when(valueOps.increment("ratelimit:chat:" + USER_ID)).thenReturn(30L);

            assertTrue(rateLimitService.tryAcquireDialogue(USER_ID));
        }

        @Test
        @DisplayName("超限(31) → 返回 false")
        void tryAcquireDialogue_exceeded_shouldReturnFalse() {
            when(valueOps.increment("ratelimit:chat:" + USER_ID)).thenReturn(31L);

            assertFalse(rateLimitService.tryAcquireDialogue(USER_ID));
        }
    }

    // ==================== tryAcquireUpload ====================

    @Nested
    @DisplayName("tryAcquireUpload")
    class UploadTests {

        @Test
        @DisplayName("首次请求 → 设置过期时间，返回 true")
        void tryAcquireUpload_firstCall_shouldSetExpireAndReturnTrue() {
            when(valueOps.increment("ratelimit:upload:" + USER_ID)).thenReturn(1L);

            assertTrue(rateLimitService.tryAcquireUpload(USER_ID));
            verify(redis).expire("ratelimit:upload:" + USER_ID, 60, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("已达上限(10) → 返回 true")
        void tryAcquireUpload_atLimit_shouldReturnTrue() {
            when(valueOps.increment("ratelimit:upload:" + USER_ID)).thenReturn(10L);

            assertTrue(rateLimitService.tryAcquireUpload(USER_ID));
        }

        @Test
        @DisplayName("超限(11) → 返回 false")
        void tryAcquireUpload_exceeded_shouldReturnFalse() {
            when(valueOps.increment("ratelimit:upload:" + USER_ID)).thenReturn(11L);

            assertFalse(rateLimitService.tryAcquireUpload(USER_ID));
        }
    }
}
