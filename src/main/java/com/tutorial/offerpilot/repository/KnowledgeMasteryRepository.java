/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.KnowledgeMastery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeMasteryRepository extends JpaRepository<KnowledgeMastery, Long> {

    List<KnowledgeMastery> findByUserId(String userId);

    Optional<KnowledgeMastery> findByUserIdAndKnowledgePoint(String userId, String knowledgePoint);

    void deleteByUserId(String userId);
}
