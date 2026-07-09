/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    Optional<AnalysisReport> findByReportId(String reportId);

    List<AnalysisReport> findByUserIdOrderByCreatedAtDesc(String userId);

    List<AnalysisReport> findBySessionId(String sessionId);
}
