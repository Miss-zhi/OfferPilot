/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.progress.ProgressResponse;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.entity.StudyPlan;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.repository.StudyPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/offerpilot/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final InterviewSessionRepository sessionRepo;
    private final KnowledgeMasteryRepository masteryRepo;
    private final StudyPlanRepository planRepo;

    @GetMapping
    public ApiResponse<ProgressResponse> getProgress(
            @RequestParam(value = "range", defaultValue = "month") String range,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = currentUser.getUsername();
        log.info("Get progress: userId={}, range={}", userId, range);

        ProgressResponse resp = new ProgressResponse();
        resp.setPeriod(range);
        resp.setInterviewCount(sessionRepo.countByUserId(userId));

        List<KnowledgeMastery> masteries = masteryRepo.findByUserId(userId);
        resp.setScoreTrend(buildScoreTrend(masteries));
        resp.setKnowledgeMastery(buildMasteryMap(masteries));
        resp.setStudyPlan(buildStudyPlanInfo(userId));

        return ApiResponse.success(resp);
    }

    private List<Integer> buildScoreTrend(List<KnowledgeMastery> masteries) {
        List<Integer> trend = new ArrayList<>();
        for (KnowledgeMastery m : masteries) {
            if (m.getPreviousScore() != null) {
                trend.add(m.getPreviousScore());
            }
            if (m.getScore() != null) {
                trend.add(m.getScore());
            }
        }
        return trend;
    }

    private Map<String, ProgressResponse.MasteryInfo> buildMasteryMap(List<KnowledgeMastery> masteries) {
        Map<String, ProgressResponse.MasteryInfo> map = new LinkedHashMap<>();
        for (KnowledgeMastery m : masteries) {
            ProgressResponse.MasteryInfo info = new ProgressResponse.MasteryInfo();
            info.setFirst(m.getPreviousScore());
            info.setCurrent(m.getScore());
            info.setTrend(determineTrend(m.getPreviousScore(), m.getScore()));
            map.put(m.getKnowledgePoint(), info);
        }
        return map;
    }

    private String determineTrend(Integer previous, Integer current) {
        if (previous == null || current == null) {
            return "stable";
        }
        if (current > previous) {
            return "up";
        }
        if (current < previous) {
            return "down";
        }
        return "stable";
    }

    private ProgressResponse.StudyPlanInfo buildStudyPlanInfo(String userId) {
        List<StudyPlan> plans = planRepo.findByUserIdAndStatus(userId, "ACTIVE");
        int completed = plans.stream()
                .mapToInt(p -> p.getCompletedCount() != null ? p.getCompletedCount() : 0)
                .sum();
        int total = plans.stream()
                .mapToInt(p -> p.getTotalCount() != null ? p.getTotalCount() : 0)
                .sum();

        ProgressResponse.StudyPlanInfo info = new ProgressResponse.StudyPlanInfo();
        info.setCompleted(completed);
        info.setTotal(total);
        return info;
    }
}
