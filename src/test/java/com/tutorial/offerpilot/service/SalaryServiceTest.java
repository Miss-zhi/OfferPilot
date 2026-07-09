/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.salary.NegotiationScriptRequest;
import com.tutorial.offerpilot.dto.salary.OfferCompareRequest;
import com.tutorial.offerpilot.dto.tool.NegotiationScriptResult;
import com.tutorial.offerpilot.dto.tool.OfferComparisonResult;
import com.tutorial.offerpilot.dto.tool.SalarySearchResult;
import com.tutorial.offerpilot.entity.SalaryRecord;
import com.tutorial.offerpilot.repository.SalaryRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SalaryService 单元测试")
class SalaryServiceTest {

    @Mock
    private SalaryRecordRepository salaryRepo;

    @InjectMocks
    private SalaryService salaryService;

    private SalaryRecord buildRecord(String company, String position, BigDecimal base) {
        SalaryRecord r = new SalaryRecord();
        r.setCompanyName(company);
        r.setPosition(position);
        r.setBaseSalary(base);
        r.setSource("内部");
        return r;
    }

    // ==================== searchSalary ====================

    @Nested
    @DisplayName("searchSalary")
    class SearchSalaryTests {

        @Test
        @DisplayName("公司+职位匹配 → 返回结果")
        void searchSalary_withCompanyAndPosition() {
            SalaryRecord r1 = buildRecord("阿里", "Java 开发", new BigDecimal("30"));
            when(salaryRepo.findAll()).thenReturn(List.of(r1));

            SalarySearchResult result = salaryService.searchSalary("阿里", "Java");

            assertEquals(1, result.getTotal());
            SalarySearchResult.SalaryItem item = result.getSalaries().get(0);
            assertEquals("阿里", item.getCompany());
            assertEquals("Java 开发", item.getPosition());
            assertTrue(item.getBaseRange().contains("K"));
        }

        @Test
        @DisplayName("仅公司匹配 → 返回结果")
        void searchSalary_onlyCompany() {
            SalaryRecord r = buildRecord("腾讯", "后端", new BigDecimal("35"));
            when(salaryRepo.findAll()).thenReturn(List.of(r));

            SalarySearchResult result = salaryService.searchSalary("腾讯", null);

            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("无匹配 → 返回 empty")
        void searchSalary_noMatch_shouldReturnEmpty() {
            when(salaryRepo.findAll()).thenReturn(Collections.emptyList());

            SalarySearchResult result = salaryService.searchSalary("不存在", "不存在");

            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("baseSalary 为 null → baseRange='暂无数据'")
        void searchSalary_nullBase_shouldShowNoData() {
            SalaryRecord r = buildRecord("阿里", "Java", null);
            when(salaryRepo.findAll()).thenReturn(List.of(r));

            SalarySearchResult result = salaryService.searchSalary("阿里", null);

            assertEquals("暂无数据", result.getSalaries().get(0).getBaseRange());
        }
    }

    // ==================== compareOffers ====================

    @Nested
    @DisplayName("compareOffers")
    class CompareOffersTests {

        @Test
        @DisplayName("2个 offer → 计算总额并推荐最高的")
        void compareOffers_twoOffers_shouldRecommendBest() {
            OfferCompareRequest req = new OfferCompareRequest();
            OfferCompareRequest.OfferItem o1 = new OfferCompareRequest.OfferItem();
            o1.setCompany("阿里");
            o1.setBase(30.0);
            o1.setMonths(16);
            o1.setBonus("3个月");
            o1.setLocation("杭州");
            OfferCompareRequest.OfferItem o2 = new OfferCompareRequest.OfferItem();
            o2.setCompany("腾讯");
            o2.setBase(35.0);
            o2.setMonths(15);

            req.setOffers(List.of(o1, o2));

            OfferComparisonResult result = salaryService.compareOffers(req);

            assertEquals(2, result.getAnalyses().size());
            assertTrue(result.getRecommendation().contains("腾讯"));
            // 按总额降序排列：腾讯(525) > 阿里(480)
            assertEquals(525.0, result.getAnalyses().get(0).getTotalPackage()); // 35*15
            assertEquals(480.0, result.getAnalyses().get(1).getTotalPackage()); // 30*16
        }

        @Test
        @DisplayName("不足 2 个 offer → 返回错误提示")
        void compareOffers_lessThanTwo_shouldReturnError() {
            OfferCompareRequest req = new OfferCompareRequest();
            OfferCompareRequest.OfferItem o1 = new OfferCompareRequest.OfferItem();
            o1.setCompany("阿里");
            o1.setBase(30.0);
            req.setOffers(List.of(o1));

            OfferComparisonResult result = salaryService.compareOffers(req);

            assertEquals("offer数量不足", result.getSummary());
            assertEquals("请提供至少两个offer进行对比", result.getRecommendation());
        }

        @Test
        @DisplayName("offer 的 months 为 null → 默认 12")
        void compareOffers_nullMonths_shouldDefaultTo12() {
            OfferCompareRequest req = new OfferCompareRequest();
            OfferCompareRequest.OfferItem o1 = new OfferCompareRequest.OfferItem();
            o1.setCompany("阿里");
            o1.setBase(20.0);
            OfferCompareRequest.OfferItem o2 = new OfferCompareRequest.OfferItem();
            o2.setCompany("腾讯");
            o2.setBase(15.0);
            req.setOffers(List.of(o1, o2));

            OfferComparisonResult result = salaryService.compareOffers(req);

            assertEquals(240.0, result.getAnalyses().get(0).getTotalPackage()); // 20*12
        }
    }

    // ==================== generateNegotiationScript ====================

    @Nested
    @DisplayName("generateNegotiationScript")
    class NegotiationScriptTests {

        @Test
        @DisplayName("assertive 风格 → 包含对应话术")
        void generateNegotiationScript_assertive_shouldReturnScript() {
            NegotiationScriptRequest req = new NegotiationScriptRequest();
            req.setNegotiationStyle("assertive");
            req.setCurrentOffer("30万");
            req.setTargetSalary(40.0);

            NegotiationScriptResult result = salaryService.generateNegotiationScript(req);

            assertTrue(result.getOpeningLine().contains("非常感兴趣"));
            assertFalse(result.getTalkingPoints().isEmpty());
            assertFalse(result.getCounterArguments().isEmpty());
            assertTrue(result.getClosingLine().contains("显著价值"));
        }

        @Test
        @DisplayName("moderate 风格 → 默认温和话术")
        void generateNegotiationScript_moderate_shouldReturnScript() {
            NegotiationScriptRequest req = new NegotiationScriptRequest();
            req.setNegotiationStyle("moderate");
            req.setCurrentOffer("20万");

            NegotiationScriptResult result = salaryService.generateNegotiationScript(req);

            assertTrue(result.getOpeningLine().contains("珍惜"));
            assertTrue(result.getClosingLine().contains("平衡点"));
        }

        @Test
        @DisplayName("targetSalary 为 null → 不包含期望薪资行")
        void generateNegotiationScript_nullTarget_shouldSkipTargetLine() {
            NegotiationScriptRequest req = new NegotiationScriptRequest();
            req.setNegotiationStyle("assertive");
            req.setCurrentOffer("30万");
            req.setTargetSalary(null);

            NegotiationScriptResult result = salaryService.generateNegotiationScript(req);

            boolean hasTargetLine = result.getTalkingPoints().stream()
                    .anyMatch(p -> p.contains("期望薪资"));
            assertFalse(hasTargetLine);
        }
    }
}
