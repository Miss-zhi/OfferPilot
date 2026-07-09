/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.dto.tool.NegotiationScriptResult;
import com.tutorial.offerpilot.dto.tool.OfferComparisonResult;
import com.tutorial.offerpilot.dto.tool.SalarySearchResult;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.service.SalaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SalaryController Web 层测试")
class SalaryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SalaryService salaryService;

    @InjectMocks
    private SalaryController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    // ==================== GET /api/v1/offerpilot/salary/search ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/salary/search")
    class SearchTests {

        @Test
        @DisplayName("正常查询 → 200 OK + SalarySearchResult")
        void search_shouldReturn200() throws Exception {
            SalarySearchResult.SalaryItem item = new SalarySearchResult.SalaryItem(
                    "字节跳动", "后端开发", "30k-50k", "3-6个月", "期权+RSU", "Boss直聘", 0.95f);
            SalarySearchResult result = new SalarySearchResult(1, List.of(item));
            when(salaryService.searchSalary("字节跳动", "后端开发")).thenReturn(result);

            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "字节跳动")
                            .param("position", "后端开发"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.salaries[0].company").value("字节跳动"))
                    .andExpect(jsonPath("$.data.salaries[0].position").value("后端开发"))
                    .andExpect(jsonPath("$.data.salaries[0].baseRange").value("30k-50k"))
                    .andExpect(jsonPath("$.data.salaries[0].bonusRange").value("3-6个月"))
                    .andExpect(jsonPath("$.data.salaries[0].stockInfo").value("期权+RSU"))
                    .andExpect(jsonPath("$.data.salaries[0].source").value("Boss直聘"))
                    .andExpect(jsonPath("$.data.salaries[0].relevanceScore").value(0.95));

            verify(salaryService).searchSalary("字节跳动", "后端开发");
        }

        @Test
        @DisplayName("无 position 参数 → 正常查询")
        void search_noPosition_shouldStillSucceed() throws Exception {
            SalarySearchResult result = new SalarySearchResult(0, List.of());
            when(salaryService.searchSalary(eq("字节跳动"), isNull())).thenReturn(result);

            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "字节跳动"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0));
        }

        @Test
        @DisplayName("公司名称为空 → 400 参数校验失败")
        void search_blankCompany_shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "")
                            .param("position", "后端"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(salaryService, never()).searchSalary(anyString(), anyString());
        }

        @Test
        @DisplayName("无结果 → 返回空列表")
        void search_noResults_shouldReturnEmptyList() throws Exception {
            SalarySearchResult result = new SalarySearchResult(0, List.of());
            when(salaryService.searchSalary("小公司", "冷门岗位")).thenReturn(result);

            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "小公司")
                            .param("position", "冷门岗位"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.salaries").isArray())
                    .andExpect(jsonPath("$.data.salaries").isEmpty());
        }
    }

    // ==================== POST /api/v1/offerpilot/salary/compare ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/salary/compare")
    class CompareTests {

        @Test
        @DisplayName("正常对比 → 200 OK + OfferComparisonResult")
        void compare_shouldReturn200() throws Exception {
            OfferComparisonResult.OfferAnalysis analysis = new OfferComparisonResult.OfferAnalysis(
                    "字节跳动", 60.0, List.of("薪资高"), List.of("加班多"));
            OfferComparisonResult result = new OfferComparisonResult(
                    "综合建议选字节跳动", List.of(analysis), "推荐字节跳动");
            when(salaryService.compareOffers(any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/offerpilot/salary/compare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"offers": [
                                        {"company":"字节跳动","position":"后端","base":40,"months":15},
                                        {"company":"腾讯","position":"后端","base":38,"months":16}
                                    ]}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.summary").value("综合建议选字节跳动"))
                    .andExpect(jsonPath("$.data.analyses[0].company").value("字节跳动"))
                    .andExpect(jsonPath("$.data.analyses[0].totalPackage").value(60.0))
                    .andExpect(jsonPath("$.data.analyses[0].pros[0]").value("薪资高"))
                    .andExpect(jsonPath("$.data.analyses[0].cons[0]").value("加班多"))
                    .andExpect(jsonPath("$.data.recommendation").value("推荐字节跳动"));

            verify(salaryService).compareOffers(any());
        }

        @Test
        @DisplayName("offers 为空 → 400 参数校验失败")
        void compare_emptyOffers_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/salary/compare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"offers": []}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(salaryService, never()).compareOffers(any());
        }

        @Test
        @DisplayName("只有一个 offer → 正常返回对比")
        void compare_singleOffer_shouldSucceed() throws Exception {
            OfferComparisonResult result = new OfferComparisonResult(
                    "无法对比，仅有一个offer", List.of(), "无推荐");
            when(salaryService.compareOffers(any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/offerpilot/salary/compare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"offers": [
                                        {"company":"字节跳动","position":"后端","base":40,"months":15}
                                    ]}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary").value("无法对比，仅有一个offer"));
        }
    }

    // ==================== POST /api/v1/offerpilot/salary/negotiation-script ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/salary/negotiation-script")
    class NegotiationScriptTests {

        @Test
        @DisplayName("正常生成 → 200 OK + NegotiationScriptResult")
        void negotiationScript_shouldReturn200() throws Exception {
            NegotiationScriptResult result = new NegotiationScriptResult(
                    "感谢您的offer",
                    List.of("基于市场数据", "我的技能匹配"),
                    List.of("如果HR说预算有限"),
                    "期待您的回复");
            when(salaryService.generateNegotiationScript(any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "字节跳动后端40k*15", "targetSalary": 50.0, "negotiationStyle": "assertive"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.openingLine").value("感谢您的offer"))
                    .andExpect(jsonPath("$.data.talkingPoints[0]").value("基于市场数据"))
                    .andExpect(jsonPath("$.data.talkingPoints[1]").value("我的技能匹配"))
                    .andExpect(jsonPath("$.data.counterArguments[0]").value("如果HR说预算有限"))
                    .andExpect(jsonPath("$.data.closingLine").value("期待您的回复"));

            verify(salaryService).generateNegotiationScript(any());
        }

        @Test
        @DisplayName("currentOffer 为空 → 400 参数校验失败")
        void negotiationScript_blankOffer_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "", "targetSalary": 50.0}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(salaryService, never()).generateNegotiationScript(any());
        }

        @Test
        @DisplayName("默认 negotiationStyle → 使用默认值 moderate")
        void negotiationScript_defaultStyle_shouldUseDefault() throws Exception {
            NegotiationScriptResult result = new NegotiationScriptResult(
                    "开场白", List.of("论点"), List.of("反驳"), "总结");
            when(salaryService.generateNegotiationScript(any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "腾讯38k*16"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.openingLine").value("开场白"));
        }

        @Test
        @DisplayName("服务层异常 → 异常透传")
        void negotiationScript_serviceError_shouldPropagate() throws Exception {
            when(salaryService.generateNegotiationScript(any()))
                    .thenThrow(new BusinessException(500, "生成失败"));

            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "测试offer"}"""))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("生成失败"));
        }
    }
}
