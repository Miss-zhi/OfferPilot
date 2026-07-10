/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

@DisplayName("SalaryService 集成测试")
class SalaryServiceIT extends AbstractServiceIT {

    @Autowired
    private SalaryService salaryService;

    @Autowired
    private SalaryRecordRepository salaryRepo;

    // ==================== searchSalary ====================

    @Nested
    @DisplayName("searchSalary")
    class SearchSalaryTests {

        @Test
        @DisplayName("按公司名搜索 → 返回匹配记录")
        void searchSalary_byCompany_shouldReturnMatches() {
            // Arrange: create salary records via JPA
            createSalaryRecord("字节跳动", "Java开发工程师", 35000);
            createSalaryRecord("阿里巴巴", "Java高级工程师", 40000);
            createSalaryRecord("腾讯", "C++开发工程师", 38000);

            // Act
            SalarySearchResult result = salaryService.searchSalary("字节跳动", null);

            // Assert
            assertNotNull(result);
            assertTrue(result.getTotal() >= 1);
            assertTrue(result.getSalaries().stream()
                    .anyMatch(s -> "字节跳动".equals(s.getCompany())));
        }

        @Test
        @DisplayName("按公司+职位搜索 → 精确过滤")
        void searchSalary_byCompanyAndPosition_shouldFilter() {
            createSalaryRecord("字节跳动", "Java开发工程师", 35000);
            createSalaryRecord("阿里巴巴", "Java高级工程师", 40000);
            createSalaryRecord("腾讯", "C++开发工程师", 38000);

            SalarySearchResult result = salaryService.searchSalary("阿里", "Java");

            assertTrue(result.getTotal() >= 1);
            assertTrue(result.getSalaries().stream()
                    .allMatch(s -> s.getCompany().contains("阿里") && s.getPosition().contains("Java")));
        }

        @Test
        @DisplayName("无匹配 → 返回空列表")
        void searchSalary_noMatch_shouldReturnEmpty() {
            createSalaryRecord("字节跳动", "Java开发工程师", 35000);

            SalarySearchResult result = salaryService.searchSalary("不存在的公司", null);

            assertEquals(0, result.getTotal());
            assertTrue(result.getSalaries().isEmpty());
        }

        private void createSalaryRecord(String company, String position, int base) {
            SalaryRecord r = new SalaryRecord();
            r.setUserId("salary-test");
            r.setCompanyName(company);
            r.setPosition(position);
            r.setBaseSalary(BigDecimal.valueOf(base));
            r.setCreateBy("test");
            salaryRepo.saveAndFlush(r);
        }
    }

    // ==================== compareOffers ====================

    @Nested
    @DisplayName("compareOffers")
    class CompareOffersTests {

        @Test
        @DisplayName("2个Offer → 返回对比结果")
        void compareOffers_twoOffers_shouldReturnComparison() {
            OfferCompareRequest req = new OfferCompareRequest();
            OfferCompareRequest.OfferItem offer1 = new OfferCompareRequest.OfferItem();
            offer1.setCompany("公司A");
            offer1.setBase(30.0);
            offer1.setMonths(14);
            offer1.setBonus("2个月年终");
            offer1.setStock("期权10万股");

            OfferCompareRequest.OfferItem offer2 = new OfferCompareRequest.OfferItem();
            offer2.setCompany("公司B");
            offer2.setBase(40.0);
            offer2.setMonths(15);
            offer2.setLocation("北京");

            req.setOffers(List.of(offer1, offer2));

            OfferComparisonResult result = salaryService.compareOffers(req);

            assertNotNull(result.getSummary());
            assertFalse(result.getSummary().isEmpty());
            assertEquals(2, result.getAnalyses().size());
            assertNotNull(result.getRecommendation());
            assertTrue(result.getRecommendation().contains("公司B"));
        }

        @Test
        @DisplayName("少于2个Offer → 返回错误信息")
        void compareOffers_lessThanTwo_shouldReturnError() {
            OfferCompareRequest req = new OfferCompareRequest();
            OfferCompareRequest.OfferItem offer1 = new OfferCompareRequest.OfferItem();
            offer1.setCompany("公司A");
            offer1.setBase(30.0);
            req.setOffers(List.of(offer1));

            OfferComparisonResult result = salaryService.compareOffers(req);

            assertEquals("offer数量不足", result.getSummary());
            assertTrue(result.getAnalyses().isEmpty());
        }
    }

    // ==================== generateNegotiationScript ====================

    @Nested
    @DisplayName("generateNegotiationScript")
    class NegotiationScriptTests {

        @Test
        @DisplayName("assertive风格 → 生成自信话术")
        void generateScript_assertive_shouldGenerateAssertiveScript() {
            NegotiationScriptRequest req = new NegotiationScriptRequest();
            req.setCurrentOffer("公司A offer 30K*15");
            req.setTargetSalary(40.0);
            req.setNegotiationStyle("assertive");

            NegotiationScriptResult result = salaryService.generateNegotiationScript(req);

            assertNotNull(result.getOpeningLine());
            assertTrue(result.getOpeningLine().contains("非常感谢"));
            assertTrue(result.getTalkingPoints().size() >= 3);
            assertTrue(result.getCounterArguments().size() >= 2);
            assertNotNull(result.getClosingLine());
            assertTrue(result.getClosingLine().contains("达成共识"));
        }

        @Test
        @DisplayName("moderate风格 → 生成温和话术")
        void generateScript_moderate_shouldGenerateModerateScript() {
            NegotiationScriptRequest req = new NegotiationScriptRequest();
            req.setCurrentOffer("公司B offer 25K*13");
            req.setTargetSalary(30.0);
            req.setNegotiationStyle("moderate");

            NegotiationScriptResult result = salaryService.generateNegotiationScript(req);

            assertNotNull(result.getOpeningLine());
            assertTrue(result.getOpeningLine().contains("珍惜"));
            assertTrue(result.getTalkingPoints().size() >= 3);
            assertNotNull(result.getClosingLine());
            assertTrue(result.getClosingLine().contains("平衡"));
        }

        @Test
        @DisplayName("默认风格(conservative) → 保守话术")
        void generateScript_default_shouldGenerateDefaultScript() {
            NegotiationScriptRequest req = new NegotiationScriptRequest();
            req.setCurrentOffer("某公司 offer");
            req.setNegotiationStyle("conservative");

            NegotiationScriptResult result = salaryService.generateNegotiationScript(req);

            assertNotNull(result.getOpeningLine());
            assertTrue(result.getOpeningLine().contains("进一步了解"));
            assertNotNull(result.getClosingLine());
            assertTrue(result.getClosingLine().contains("进一步交流"));
        }
    }
}
