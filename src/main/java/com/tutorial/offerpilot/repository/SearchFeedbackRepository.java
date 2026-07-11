/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.SearchFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchFeedbackRepository extends JpaRepository<SearchFeedback, Long> {

    List<SearchFeedback> findByUserIdOrderByCreatedAtDesc(String userId);

    List<SearchFeedback> findBySessionId(String sessionId);
}
