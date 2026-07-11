/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    List<StudyPlan> findByUserIdOrderByWeekStartDesc(String userId);

    List<StudyPlan> findByUserIdAndStatus(String userId, String status);

    List<StudyPlan> findByStatus(String status);
}
