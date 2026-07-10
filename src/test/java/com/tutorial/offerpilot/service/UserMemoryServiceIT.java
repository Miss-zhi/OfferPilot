/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("UserMemoryService 集成测试")
class UserMemoryServiceIT extends AbstractServiceIT {

    @Autowired
    private UserMemoryService memoryService;

    // ==================== saveMemory ====================

    @Nested
    @DisplayName("saveMemory")
    class SaveMemoryTests {

        @Test
        @DisplayName("新记忆 → 创建记录")
        void saveMemory_new_shouldCreateRecord() {
            memoryService.saveMemory("mem-user-1", "skill-java", "精通Java并发编程", "PROFILE");

            runVerify("user-memory/save-new");
        }

        @Test
        @DisplayName("重复 key → 更新内容")
        void saveMemory_existingKey_shouldUpdateContent() {
            memoryService.saveMemory("mem-user-2", "skill-db", "掌握MySQL", "PROFILE");
            memoryService.saveMemory("mem-user-2", "skill-db", "精通MySQL优化", "PROFILE");

            runVerify("user-memory/save-update");
        }
    }

    // ==================== loadUserMemory ====================

    @Nested
    @DisplayName("loadUserMemory")
    class LoadMemoryTests {

        @Test
        @DisplayName("有记忆 → 返回格式化文本 + 更新 accessCount")
        void loadUserMemory_withData_shouldReturnFormattedText() {
            runSetup("user-memory/load");

            String result = memoryService.loadUserMemory("mem-load-user");

            assertNotNull(result);
            // Verify the memory output structure
            assertTrue(result.startsWith("# 用户记忆"),
                    "Expected memory header, got: " + result.substring(0, Math.min(result.length(), 100)));
            runVerify("user-memory/load");
        }

        @Test
        @DisplayName("无记忆 → 返回 null")
        void loadUserMemory_noData_shouldReturnNull() {
            String result = memoryService.loadUserMemory("mem-no-data-user");

            assertNull(result);
        }
    }

    // ==================== removeMemory ====================

    @Nested
    @DisplayName("removeMemory")
    class RemoveMemoryTests {

        @Test
        @DisplayName("删除已有记忆 → 记录消失")
        void removeMemory_existing_shouldDeleteRecord() {
            memoryService.saveMemory("mem-rm-user", "weak-point", "算法薄弱", "WEAK_POINT");

            memoryService.removeMemory("mem-rm-user", "weak-point");

            runVerify("user-memory/remove");
        }
    }
}
