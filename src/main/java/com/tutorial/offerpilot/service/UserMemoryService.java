/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.UserMemory;
import com.tutorial.offerpilot.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryService {

    private final UserMemoryRepository memoryRepo;

    /** Agent 读取用户记忆（注入到 system prompt） */
    public String loadUserMemory(String userId) {
        List<UserMemory> memories = memoryRepo.findByUserIdOrderByRelevanceScoreDesc(userId);
        if (memories.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("# 用户记忆\n\n");
        Map<String, List<UserMemory>> grouped = memories.stream()
                .collect(Collectors.groupingBy(UserMemory::getCategory));

        grouped.forEach((category, list) -> {
            sb.append("## ").append(categoryLabel(category)).append("\n");
            list.forEach(m -> sb.append("- ").append(m.getMemoryContent()).append("\n"));
            sb.append("\n");
        });

        // 更新访问计数
        memories.forEach(m -> {
            m.setAccessCount(m.getAccessCount() + 1);
            m.setLastAccessed(Instant.now());
        });
        memoryRepo.saveAll(memories);

        return sb.toString();
    }

    /** Agent 写入/更新记忆 */
    @Transactional
    public void saveMemory(String userId, String key, String content, String category) {
        UserMemory memory = memoryRepo.findByUserIdAndMemoryKey(userId, key)
                .orElseGet(() -> {
                    UserMemory m = new UserMemory();
                    m.setUserId(userId);
                    m.setMemoryKey(key);
                    return m;
                });
        memory.setMemoryContent(content);
        memory.setCategory(category);
        memoryRepo.save(memory);
    }

    /** Agent 删除过时记忆 */
    @Transactional
    public void removeMemory(String userId, String key) {
        memoryRepo.deleteByUserIdAndMemoryKey(userId, key);
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "PROFILE" -> "用户画像";
            case "WEAK_POINT" -> "薄弱点追踪";
            case "PREFERENCE" -> "面试偏好";
            case "PLAN" -> "学习计划";
            default -> "其他记忆";
        };
    }
}
