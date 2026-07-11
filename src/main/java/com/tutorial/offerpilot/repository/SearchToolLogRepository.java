/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.SearchToolLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SearchToolLogRepository extends JpaRepository<SearchToolLog, Long> {

    List<SearchToolLog> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT s.queryText, COUNT(s) FROM SearchToolLog s GROUP BY s.queryText ORDER BY COUNT(s) DESC")
    List<Object[]> findTopQueries();

    @Query("SELECT s FROM SearchToolLog s WHERE s.zeroResult = 1 ORDER BY s.createdAt DESC")
    List<SearchToolLog> findZeroResultLogs();
}
