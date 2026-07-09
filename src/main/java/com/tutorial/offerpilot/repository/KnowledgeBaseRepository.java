/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository extends JpaRepository<KbKnowledgeBase, Long> {

    Optional<KbKnowledgeBase> findByKbId(String kbId);

    List<KbKnowledgeBase> findByVisibility(String visibility);

    List<KbKnowledgeBase> findByOwnerId(String ownerId);

    @Query("SELECT k FROM KbKnowledgeBase k WHERE k.visibility = 'PUBLIC' OR k.ownerId = :userId")
    List<KbKnowledgeBase> findPublicOrOwnedBy(@Param("userId") String userId);

    List<KbKnowledgeBase> findByOwnerIdAndVisibility(String ownerId, String visibility);

    void deleteByKbId(String kbId);
}
