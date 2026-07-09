/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.KbSearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchLogRepository extends JpaRepository<KbSearchLog, Long> {

    List<KbSearchLog> findByKbIdOrderByCreatedAtDesc(String kbId);
}
