/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserIdOrderByRelevanceScoreDesc(String userId);

    Optional<UserMemory> findByUserIdAndMemoryKey(String userId, String memoryKey);

    void deleteByUserIdAndMemoryKey(String userId, String memoryKey);

    void deleteByUserId(String userId);
}
