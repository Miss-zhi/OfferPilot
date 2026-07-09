/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.KbChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChunkRepository extends JpaRepository<KbChunk, Long> {

    List<KbChunk> findByDocIdOrderByChunkIndex(String docId);

    List<KbChunk> findByKbId(String kbId);

    long countByDocId(String docId);

    long countByKbId(String kbId);

    void deleteByDocId(String docId);

    void deleteByKbId(String kbId);
}
