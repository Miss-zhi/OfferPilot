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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@DisplayName("SearchResultCacheService 集成测试")
class SearchResultCacheServiceIT extends AbstractServiceIT {

    @Autowired
    private SearchResultCacheService cacheService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @BeforeEach
    void setUpRedisMock() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ==================== cacheSearchResult & getCachedSearchResult ====================

    @Nested
    @DisplayName("检索结果缓存")
    class SearchResultCacheTests {

        @Test
        @DisplayName("缓存搜索结果 → 使用正确的 Key 格式和 10 分钟 TTL")
        void cacheSearchResult_shouldUseCorrectKeyAndTtl() {
            cacheService.cacheSearchResult("kb-001", "Java面试", "{\"hits\":[]}");

            // Key = "search:kbId:" + MD5(query)
            verify(valueOps).set(startsWith("search:kb-001:"), eq("{\"hits\":[]}"),
                    eq(10L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("相同 query 的 Key 应一致（MD5 确定性）")
        void sameQuery_shouldProduceSameKey() {
            cacheService.cacheSearchResult("kb-001", "Java面试", "result1");
            cacheService.cacheSearchResult("kb-001", "Java面试", "result2");

            // 两次调用应使用相同的 key
            verify(valueOps, times(2)).set(startsWith("search:kb-001:"), anyString(),
                    eq(10L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("不同 query 的 Key 应不同")
        void differentQuery_shouldProduceDifferentKey() {
            cacheService.cacheSearchResult("kb-001", "Java", "result1");
            cacheService.cacheSearchResult("kb-001", "Python", "result2");

            verify(valueOps).set(startsWith("search:kb-001:"), eq("result1"),
                    eq(10L), eq(TimeUnit.MINUTES));
            verify(valueOps).set(startsWith("search:kb-001:"), eq("result2"),
                    eq(10L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("缓存命中 → 返回 Optional.of(value)")
        void getCachedSearchResult_hit_shouldReturnValue() {
            String expectedKey = "search:kb-002:" + expectedMd5("Redis");
            when(valueOps.get(expectedKey)).thenReturn("{\"hits\":[{\"content\":\"test\"}]}");

            Optional<String> result = cacheService.getCachedSearchResult("kb-002", "Redis");

            assertTrue(result.isPresent());
            assertTrue(result.get().contains("hits"));
        }

        @Test
        @DisplayName("缓存未命中 → 返回 Optional.empty()")
        void getCachedSearchResult_miss_shouldReturnEmpty() {
            when(valueOps.get(anyString())).thenReturn(null);

            Optional<String> result = cacheService.getCachedSearchResult("kb-003", "NoResult");

            assertFalse(result.isPresent());
        }
    }

    // ==================== cacheUserProfile & getCachedUserProfile ====================

    @Nested
    @DisplayName("用户画像缓存")
    class UserProfileCacheTests {

        @Test
        @DisplayName("缓存用户画像 → 使用正确的 Key 格式和 5 分钟 TTL")
        void cacheUserProfile_shouldUseCorrectKeyAndTtl() {
            cacheService.cacheUserProfile("user-001", "{\"name\":\"张三\"}");

            verify(valueOps).set(eq("profile:user-001"), eq("{\"name\":\"张三\"}"),
                    eq(5L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("读取缓存的用户画像 → 返回 Optional.of(value)")
        void getCachedUserProfile_hit_shouldReturnValue() {
            when(valueOps.get("profile:user-002")).thenReturn("{\"targetCompany\":\"字节跳动\"}");

            Optional<String> result = cacheService.getCachedUserProfile("user-002");

            assertTrue(result.isPresent());
            assertTrue(result.get().contains("字节跳动"));
        }

        @Test
        @DisplayName("读取未缓存的用户画像 → 返回 Optional.empty()")
        void getCachedUserProfile_miss_shouldReturnEmpty() {
            when(valueOps.get(anyString())).thenReturn(null);

            Optional<String> result = cacheService.getCachedUserProfile("unknown");

            assertFalse(result.isPresent());
        }
    }

    // ==================== Key 隔离 ====================

    @Nested
    @DisplayName("Key 隔离")
    class KeyIsolationTests {

        @Test
        @DisplayName("不同 kbId 使用不同的前缀")
        void differentKbId_shouldUseDifferentPrefix() {
            cacheService.cacheSearchResult("kb-A", "query", "r1");
            cacheService.cacheSearchResult("kb-B", "query", "r2");

            verify(valueOps).set(startsWith("search:kb-A:"), anyString(), anyLong(), any());
            verify(valueOps).set(startsWith("search:kb-B:"), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("搜索缓存和用户画像缓存使用不同的 Key 前缀")
        void searchAndProfile_shouldUseDifferentPrefixes() {
            cacheService.cacheSearchResult("kb-001", "test", "r");
            cacheService.cacheUserProfile("user-001", "p");

            verify(valueOps).set(startsWith("search:"), anyString(), anyLong(), any());
            verify(valueOps).set(startsWith("profile:"), anyString(), anyLong(), any());
        }
    }

    /** 计算 expected MD5（与 SearchResultCacheService 中的一致） */
    private String expectedMd5(String query) {
        return org.springframework.util.DigestUtils.md5DigestAsHex(query.getBytes());
    }
}
