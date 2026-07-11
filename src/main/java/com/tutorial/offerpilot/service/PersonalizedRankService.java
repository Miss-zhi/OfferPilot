/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 个性化排序服务。
 * 基于用户的知识掌握薄弱点，对搜索结果进行加权排序，
 * 弱项相关的题目优先展示，提升面试准备的针对性。
 */
@Slf4j
@Service
public class PersonalizedRankService {

    private static final float WEAK_POINT_BOOST = 1.3f;
    private static final int WEAK_SCORE_THRESHOLD = 60;

    private final KnowledgeMasteryRepository masteryRepo;

    public PersonalizedRankService(KnowledgeMasteryRepository masteryRepo) {
        this.masteryRepo = masteryRepo;
    }

    /**
     * 获取用户的薄弱知识点列表（score < 60）。
     */
    public Set<String> getWeakPoints(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptySet();
        }

        List<KnowledgeMastery> masteries = masteryRepo.findByUserId(userId);
        return masteries.stream()
                .filter(m -> m.getScore() != null && m.getScore() < WEAK_SCORE_THRESHOLD)
                .map(KnowledgeMastery::getKnowledgePoint)
                .collect(Collectors.toSet());
    }

    /**
     * 计算搜索结果的个性化加权分数。
     *
     * @param content        搜索结果内容文本
     * @param baseScore      原始相关性分数
     * @param weakPoints     用户薄弱知识点集合
     * @return 加权后的分数（薄弱点相关 ×1.3，否则保持原值）
     */
    public float boostScore(String content, float baseScore, Set<String> weakPoints) {
        if (content == null || weakPoints.isEmpty()) {
            return baseScore;
        }

        String lowerContent = content.toLowerCase();
        for (String weakPoint : weakPoints) {
            if (lowerContent.contains(weakPoint.toLowerCase())) {
                return baseScore * WEAK_POINT_BOOST;
            }
        }
        return baseScore;
    }
}
