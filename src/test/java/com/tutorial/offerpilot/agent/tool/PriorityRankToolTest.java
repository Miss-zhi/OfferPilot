/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.PriorityResult;
import com.tutorial.offerpilot.entity.KnowledgeMastery;
import com.tutorial.offerpilot.repository.KnowledgeMasteryRepository;
import com.tutorial.offerpilot.service.SearchAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriorityRankTool 单元测试")
class PriorityRankToolTest {

    @Mock
    private KnowledgeMasteryRepository masteryRepo;

    @Mock
    private SearchAnalyticsService analyticsService;

    @InjectMocks
    private PriorityRankTool tool;

    // ==================== 优先级计算 ====================

    @Nested
    @DisplayName("prioritize - 优先级计算")
    class PriorityCalculationTests {

        @Test
        @DisplayName("高考频低掌握度 → 优先级最高")
        void highFreqLowMastery_shouldBeTopPriority() {
            KnowledgeMastery algo = createMastery("算法", 30);
            KnowledgeMastery design = createMastery("系统设计", 80);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(algo, design));

            Map<String, Integer> freq = new LinkedHashMap<>();
            freq.put("算法", 100);
            freq.put("系统设计", 50);
            when(analyticsService.getTopicFrequency()).thenReturn(freq);

            PriorityResult result = tool.prioritize("user1");

            assertEquals(2, result.getItems().size());
            // 算法: 100 * (100-30) = 7000, 系统设计: 50 * (100-80) = 1000
            assertEquals("算法", result.getItems().get(0).getTopic());
            assertEquals(7000, result.getItems().get(0).getPriority());
            assertEquals("HIGH", result.getItems().get(0).getUrgency());
            assertEquals("系统设计", result.getItems().get(1).getTopic());
            assertEquals(1000, result.getItems().get(1).getPriority());
            assertEquals("LOW", result.getItems().get(1).getUrgency());
        }

        @Test
        @DisplayName("相同掌握度时考频高的排前面")
        void sameMastery_shouldSortByFrequency() {
            KnowledgeMastery a = createMastery("Java", 50);
            KnowledgeMastery b = createMastery("Python", 50);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(a, b));

            Map<String, Integer> freq = new LinkedHashMap<>();
            freq.put("Java", 80);
            freq.put("Python", 30);
            when(analyticsService.getTopicFrequency()).thenReturn(freq);

            PriorityResult result = tool.prioritize("user1");

            // Java: 80*50=4000, Python: 30*50=1500
            assertEquals("Java", result.getItems().get(0).getTopic());
            assertEquals("Python", result.getItems().get(1).getTopic());
        }

        @Test
        @DisplayName("未知知识点 → 默认考频为 1")
        void unknownTopic_shouldDefaultFrequencyToOne() {
            KnowledgeMastery m = createMastery("新知识点", 40);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(m));
            when(analyticsService.getTopicFrequency()).thenReturn(Map.of());

            PriorityResult result = tool.prioritize("user1");

            assertEquals(1, result.getItems().get(0).getFrequency());
            assertEquals(60, result.getItems().get(0).getPriority()); // 1*(100-40)=60
        }

        @Test
        @DisplayName("score 为 null → 视为 0 分")
        void nullScore_shouldTreatAsZero() {
            KnowledgeMastery m = createMastery("Spring", null);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(m));

            Map<String, Integer> freq = new LinkedHashMap<>();
            freq.put("Spring", 50);
            when(analyticsService.getTopicFrequency()).thenReturn(freq);

            PriorityResult result = tool.prioritize("user1");

            assertEquals(5000, result.getItems().get(0).getPriority()); // 50*100=5000
            assertEquals("HIGH", result.getItems().get(0).getUrgency());
        }
    }

    // ==================== Guidance 构建 ====================

    @Nested
    @DisplayName("prioritize - Guidance 构建")
    class GuidanceTests {

        @Test
        @DisplayName("guidance 包含排序规则说明")
        void guidance_shouldContainSortingRule() {
            KnowledgeMastery m = createMastery("算法", 50);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(m));
            when(analyticsService.getTopicFrequency()).thenReturn(Map.of());

            PriorityResult result = tool.prioritize("user1");

            assertNotNull(result.getGuidance());
            assertTrue(result.getGuidance().contains("高频考点 × 低掌握度"));
            assertTrue(result.getGuidance().contains("算法"));
        }

        @Test
        @DisplayName("guidance 包含 Markdown 表格")
        void guidance_shouldContainMarkdownTable() {
            KnowledgeMastery m = createMastery("设计模式", 60);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(m));
            when(analyticsService.getTopicFrequency()).thenReturn(Map.of());

            PriorityResult result = tool.prioritize("user1");

            assertTrue(result.getGuidance().contains("| 优先级 |"));
            assertTrue(result.getGuidance().contains("|--------|"));
            assertTrue(result.getGuidance().contains("设计模式"));
        }
    }

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("prioritize - 边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("用户无知识点记录 → 返回空列表")
        void noMasteries_shouldReturnEmptyList() {
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of());
            when(analyticsService.getTopicFrequency()).thenReturn(Map.of());

            PriorityResult result = tool.prioritize("user1");

            assertTrue(result.getItems().isEmpty());
            assertNotNull(result.getGuidance());
        }

        @Test
        @DisplayName("考频为空 → 所有知识点默认考频 1")
        void emptyFrequency_shouldDefaultAllToOne() {
            KnowledgeMastery a = createMastery("A", 30);
            KnowledgeMastery b = createMastery("B", 70);
            when(masteryRepo.findByUserId("user1")).thenReturn(List.of(a, b));
            when(analyticsService.getTopicFrequency()).thenReturn(Map.of());

            PriorityResult result = tool.prioritize("user1");

            // A: 1*70=70, B: 1*30=30
            assertEquals("A", result.getItems().get(0).getTopic());
            assertEquals(70, result.getItems().get(0).getPriority());
        }
    }

    // ======================== 辅助方法 ========================

    private KnowledgeMastery createMastery(String knowledgePoint, Integer score) {
        KnowledgeMastery m = new KnowledgeMastery();
        m.setUserId("user1");
        m.setKnowledgePoint(knowledgePoint);
        m.setScore(score);
        return m;
    }
}
