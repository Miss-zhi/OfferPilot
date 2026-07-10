/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.KbDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<KbDocument, Long> {

    Optional<KbDocument> findByDocId(String docId);

    List<KbDocument> findByKbId(String kbId);

    List<KbDocument> findByKbIdAndStatus(String kbId, String status);

    long countByKbId(String kbId);

    long countByKbIdAndStatus(String kbId, String status);

    void deleteByKbId(String kbId);
}
