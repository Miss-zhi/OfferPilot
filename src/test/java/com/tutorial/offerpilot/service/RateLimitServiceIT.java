/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

@DisplayName("RateLimitService 集成测试")
class RateLimitServiceIT extends AbstractServiceIT {

    @Autowired
    private RateLimitService rateLimitService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @BeforeEach
    void setUpRedisMock() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ==================== tryAcquireDialogue ====================

    @Nested
    @DisplayName("tryAcquireDialogue")
    class DialogueTests {

        @Test
        @DisplayName("首次请求 → 返回 true，设置 60 秒 TTL")
        void firstRequest_shouldReturnTrueAndSetTtl() {
            String key = "ratelimit:chat:user-001";
            when(valueOps.increment(key)).thenReturn(1L);

            boolean result = rateLimitService.tryAcquireDialogue("user-001");

            assertTrue(result);
            verify(valueOps).increment(key);
            verify(stringRedisTemplate).expire(key, 60, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("30 次请求 → 返回 true")
        void request30_shouldReturnTrue() {
            String key = "ratelimit:chat:user-002";
            when(valueOps.increment(key)).thenReturn(30L);

            boolean result = rateLimitService.tryAcquireDialogue("user-002");

            assertTrue(result);
            // 非首次 increment，不设置 TTL
            verify(stringRedisTemplate, never()).expire(eq(key), anyLong(), any());
        }

        @Test
        @DisplayName("第 31 次请求 → 返回 false（触发限流）")
        void request31_shouldReturnFalse() {
            String key = "ratelimit:chat:user-003";
            when(valueOps.increment(key)).thenReturn(31L);

            boolean result = rateLimitService.tryAcquireDialogue("user-003");

            assertFalse(result);
        }

        @Test
        @DisplayName("increment 返回 null → 返回 false")
        void nullIncrement_shouldReturnFalse() {
            String key = "ratelimit:chat:user-004";
            when(valueOps.increment(key)).thenReturn(null);

            boolean result = rateLimitService.tryAcquireDialogue("user-004");

            assertFalse(result);
        }
    }

    // ==================== tryAcquireUpload ====================

    @Nested
    @DisplayName("tryAcquireUpload")
    class UploadTests {

        @Test
        @DisplayName("首次上传 → 返回 true，设置 60 秒 TTL")
        void firstUpload_shouldReturnTrueAndSetTtl() {
            String key = "ratelimit:upload:user-001";
            when(valueOps.increment(key)).thenReturn(1L);

            boolean result = rateLimitService.tryAcquireUpload("user-001");

            assertTrue(result);
            verify(valueOps).increment(key);
            verify(stringRedisTemplate).expire(key, 60, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("10 次上传 → 返回 true")
        void request10_shouldReturnTrue() {
            String key = "ratelimit:upload:user-005";
            when(valueOps.increment(key)).thenReturn(10L);

            boolean result = rateLimitService.tryAcquireUpload("user-005");

            assertTrue(result);
        }

        @Test
        @DisplayName("第 11 次上传 → 返回 false（触发限流）")
        void request11_shouldReturnFalse() {
            String key = "ratelimit:upload:user-006";
            when(valueOps.increment(key)).thenReturn(11L);

            boolean result = rateLimitService.tryAcquireUpload("user-006");

            assertFalse(result);
        }

        @Test
        @DisplayName("increment 返回 null → 返回 false")
        void nullIncrement_shouldReturnFalse() {
            String key = "ratelimit:upload:user-007";
            when(valueOps.increment(key)).thenReturn(null);

            boolean result = rateLimitService.tryAcquireUpload("user-007");

            assertFalse(result);
        }
    }

    // ==================== key 隔离 ====================

    @Nested
    @DisplayName("Key 隔离")
    class KeyIsolationTests {

        @Test
        @DisplayName("不同 userId 使用不同的 Redis Key")
        void differentUsers_shouldUseDifferentKeys() {
            when(valueOps.increment(anyString())).thenReturn(1L);

            rateLimitService.tryAcquireDialogue("user-A");
            rateLimitService.tryAcquireDialogue("user-B");

            verify(valueOps).increment("ratelimit:chat:user-A");
            verify(valueOps).increment("ratelimit:chat:user-B");
        }

        @Test
        @DisplayName("对话和上传使用不同的 Key 前缀")
        void dialogueAndUpload_shouldUseDifferentPrefixes() {
            when(valueOps.increment(anyString())).thenReturn(1L);

            rateLimitService.tryAcquireDialogue("user-C");
            rateLimitService.tryAcquireUpload("user-C");

            verify(valueOps).increment("ratelimit:chat:user-C");
            verify(valueOps).increment("ratelimit:upload:user-C");
        }
    }
}
