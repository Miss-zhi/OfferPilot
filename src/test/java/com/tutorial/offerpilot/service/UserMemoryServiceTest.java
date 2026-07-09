/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.UserMemory;
import com.tutorial.offerpilot.repository.UserMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserMemoryService 单元测试")
class UserMemoryServiceTest {

    @Mock
    private UserMemoryRepository memoryRepo;

    @InjectMocks
    private UserMemoryService userMemoryService;

    private static final String USER_ID = "u1";
    private static final String MEMORY_KEY = "k1";

    // ---- 辅助工厂方法 ----

    private UserMemory buildMemory(String userId, String key, String content, String category, float relevanceScore) {
        UserMemory m = new UserMemory();
        m.setUserId(userId);
        m.setMemoryKey(key);
        m.setMemoryContent(content);
        m.setCategory(category);
        m.setRelevanceScore(relevanceScore);
        m.setAccessCount(0);
        return m;
    }

    // ==================== loadUserMemory ====================

    @Nested
    @DisplayName("loadUserMemory")
    class LoadUserMemoryTests {

        @Test
        @DisplayName("有记忆时 → 返回格式化字符串，并更新访问计数")
        void loadUserMemory_withMemories_shouldReturnFormattedString() {
            UserMemory m1 = buildMemory(USER_ID, "key1", "喜欢算法题", "PREFERENCE", 0.9f);
            UserMemory m2 = buildMemory(USER_ID, "key2", "动态规划薄弱", "WEAK_POINT", 0.8f);
            when(memoryRepo.findByUserIdOrderByRelevanceScoreDesc(USER_ID)).thenReturn(List.of(m1, m2));

            String result = userMemoryService.loadUserMemory(USER_ID);

            // 返回值校验
            assertNotNull(result);
            assertTrue(result.contains("# 用户记忆"));
            assertTrue(result.contains("## 面试偏好"));
            assertTrue(result.contains("- 喜欢算法题"));
            assertTrue(result.contains("## 薄弱点追踪"));
            assertTrue(result.contains("- 动态规划薄弱"));

            // 访问计数更新校验
            assertEquals(1, m1.getAccessCount());
            assertNotNull(m1.getLastAccessed());
            assertEquals(1, m2.getAccessCount());
            assertNotNull(m2.getLastAccessed());

            // saveAll 被调用
            verify(memoryRepo).saveAll(List.of(m1, m2));
        }

        @Test
        @DisplayName("无记忆时 → 返回 null，不调 saveAll")
        void loadUserMemory_noMemories_shouldReturnNull() {
            when(memoryRepo.findByUserIdOrderByRelevanceScoreDesc(USER_ID)).thenReturn(Collections.emptyList());

            String result = userMemoryService.loadUserMemory(USER_ID);

            assertNull(result);
            verify(memoryRepo, never()).saveAll(any());
        }

        @Test
        @DisplayName("单一分类多条记忆 → Header 不重复")
        void loadUserMemory_singleCategory_shouldNotDuplicateHeader() {
            UserMemory m1 = buildMemory(USER_ID, "key1", "内容A", "PROFILE", 1.0f);
            UserMemory m2 = buildMemory(USER_ID, "key2", "内容B", "PROFILE", 0.9f);
            UserMemory m3 = buildMemory(USER_ID, "key3", "内容C", "PROFILE", 0.8f);
            when(memoryRepo.findByUserIdOrderByRelevanceScoreDesc(USER_ID)).thenReturn(List.of(m1, m2, m3));

            String result = userMemoryService.loadUserMemory(USER_ID);

            // "## 用户画像" 只出现一次
            int headerCount = result.split("## 用户画像").length - 1;
            assertEquals(1, headerCount, "同一分类 Header 不应重复");

            assertTrue(result.contains("- 内容A"));
            assertTrue(result.contains("- 内容B"));
            assertTrue(result.contains("- 内容C"));
            verify(memoryRepo).saveAll(any());
        }

        @Test
        @DisplayName("未知分类 → 显示'其他记忆'")
        void loadUserMemory_unknownCategory_shouldShowDefaultLabel() {
            UserMemory m = buildMemory(USER_ID, "key1", "未知内容", "UNKNOWN_TYPE", 0.5f);
            when(memoryRepo.findByUserIdOrderByRelevanceScoreDesc(USER_ID)).thenReturn(List.of(m));

            String result = userMemoryService.loadUserMemory(USER_ID);

            assertTrue(result.contains("## 其他记忆"));
            assertTrue(result.contains("- 未知内容"));
        }

        @Test
        @DisplayName("PLAN 分类 → 显示'学习计划'")
        void loadUserMemory_planCategory_shouldShowPlanLabel() {
            UserMemory m = buildMemory(USER_ID, "key1", "刷题计划", "PLAN", 0.5f);
            when(memoryRepo.findByUserIdOrderByRelevanceScoreDesc(USER_ID)).thenReturn(List.of(m));

            String result = userMemoryService.loadUserMemory(USER_ID);

            assertTrue(result.contains("## 学习计划"));
        }
    }

    // ==================== saveMemory ====================

    @Nested
    @DisplayName("saveMemory")
    class SaveMemoryTests {

        @Test
        @DisplayName("新 key → 创建新实体并保存")
        void saveMemory_newKey_shouldCreateNewEntity() {
            when(memoryRepo.findByUserIdAndMemoryKey(USER_ID, MEMORY_KEY)).thenReturn(Optional.empty());

            userMemoryService.saveMemory(USER_ID, MEMORY_KEY, "新内容", "PROFILE");

            ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
            verify(memoryRepo).save(captor.capture());
            UserMemory saved = captor.getValue();
            assertEquals(USER_ID, saved.getUserId());
            assertEquals(MEMORY_KEY, saved.getMemoryKey());
            assertEquals("新内容", saved.getMemoryContent());
            assertEquals("PROFILE", saved.getCategory());
        }

        @Test
        @DisplayName("已存在 key → 更新实体并保存")
        void saveMemory_existingKey_shouldUpdateEntity() {
            UserMemory existing = buildMemory(USER_ID, MEMORY_KEY, "旧内容", "GENERAL", 0.5f);
            when(memoryRepo.findByUserIdAndMemoryKey(USER_ID, MEMORY_KEY)).thenReturn(Optional.of(existing));

            userMemoryService.saveMemory(USER_ID, MEMORY_KEY, "新内容", "PROFILE");

            ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
            verify(memoryRepo).save(captor.capture());
            UserMemory saved = captor.getValue();
            assertSame(existing, saved, "应复用已有实体");
            assertEquals("新内容", saved.getMemoryContent());
            assertEquals("PROFILE", saved.getCategory());
        }

        @Test
        @DisplayName("content 可为 null")
        void saveMemory_nullContent_shouldAcceptNull() {
            when(memoryRepo.findByUserIdAndMemoryKey(USER_ID, MEMORY_KEY)).thenReturn(Optional.empty());

            assertDoesNotThrow(() ->
                    userMemoryService.saveMemory(USER_ID, MEMORY_KEY, null, "PROFILE"));

            ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
            verify(memoryRepo).save(captor.capture());
            assertNull(captor.getValue().getMemoryContent());
        }
    }

    // ==================== removeMemory ====================

    @Nested
    @DisplayName("removeMemory")
    class RemoveMemoryTests {

        @Test
        @DisplayName("正常删除 → 调用 Repository 删除方法")
        void removeMemory_validKey_shouldCallDelete() {
            userMemoryService.removeMemory(USER_ID, MEMORY_KEY);

            verify(memoryRepo).deleteByUserIdAndMemoryKey(USER_ID, MEMORY_KEY);
        }

        @Test
        @DisplayName("删除不存在的 key → 不抛异常")
        void removeMemory_nonExistingKey_shouldNotThrow() {
            doNothing().when(memoryRepo).deleteByUserIdAndMemoryKey(USER_ID, "不存在的key");

            assertDoesNotThrow(() ->
                    userMemoryService.removeMemory(USER_ID, "不存在的key"));

            verify(memoryRepo).deleteByUserIdAndMemoryKey(USER_ID, "不存在的key");
        }
    }
}
