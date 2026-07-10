/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("SalaryController 集成测试")
class SalaryControllerIT extends AbstractControllerIT {

    // ==================== GET /api/v1/offerpilot/salary/search ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/salary/search")
    class SearchTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "字节跳动"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("认证用户正常查询 → 200 + 薪资数据")
        void authenticated_withValidParams_shouldReturn200() throws Exception {
            String token = registerUserAndGetToken("saluser");

            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "字节跳动")
                            .param("position", "后端开发")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").isNumber())
                    .andExpect(jsonPath("$.data.salaries").isArray());
        }

        @Test
        @DisplayName("仅有公司名 → 正常查询")
        void search_onlyCompany_shouldSucceed() throws Exception {
            String token = registerUserAndGetToken("salonly");

            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "腾讯")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("公司名为空 → 400 参数校验失败")
        void search_blankCompany_shouldReturn400() throws Exception {
            String token = registerUserAndGetToken("salblank");

            mockMvc.perform(get("/api/v1/offerpilot/salary/search")
                            .param("company", "")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== POST /api/v1/offerpilot/salary/compare ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/salary/compare")
    class CompareTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/salary/compare")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"offers": [{"company":"A","base":40,"months":15}]}"""))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("正常对比 → 200 + 对比结果")
        void compare_withValidData_shouldReturn200() throws Exception {
            String token = registerUserAndGetToken("cmpuser");

            mockMvc.perform(post("/api/v1/offerpilot/salary/compare")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"offers": [
                                        {"company":"字节跳动","position":"后端","base":40,"months":15},
                                        {"company":"腾讯","position":"后端","base":38,"months":16}
                                    ]}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.summary").isNotEmpty())
                    .andExpect(jsonPath("$.data.analyses").isArray())
                    .andExpect(jsonPath("$.data.analyses.length()").value(2))
                    .andExpect(jsonPath("$.data.recommendation").isNotEmpty());
        }

        @Test
        @DisplayName("单个 offer → 返回提示")
        void compare_singleOffer_shouldReturnHint() throws Exception {
            String token = registerUserAndGetToken("cmpone");

            mockMvc.perform(post("/api/v1/offerpilot/salary/compare")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"offers": [
                                        {"company":"字节跳动","base":40,"months":15}
                                    ]}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.summary").value("offer数量不足"));
        }

        @Test
        @DisplayName("offers 为空 → 400 参数校验失败")
        void compare_emptyOffers_shouldReturn400() throws Exception {
            String token = registerUserAndGetToken("cmpempty");

            mockMvc.perform(post("/api/v1/offerpilot/salary/compare")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"offers\": []}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== POST /api/v1/offerpilot/salary/negotiation-script ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/salary/negotiation-script")
    class NegotiationScriptTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "字节跳动40k*15"}"""))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("正常生成 → 200 + 谈判脚本")
        void negotiationScript_withValidData_shouldReturn200() throws Exception {
            String token = registerUserAndGetToken("neguser");

            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "字节跳动后端40k*15", "targetSalary": 50.0, "negotiationStyle": "assertive"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.openingLine").isNotEmpty())
                    .andExpect(jsonPath("$.data.talkingPoints").isArray())
                    .andExpect(jsonPath("$.data.talkingPoints.length()").value(greaterThan(0)))
                    .andExpect(jsonPath("$.data.counterArguments").isArray())
                    .andExpect(jsonPath("$.data.closingLine").isNotEmpty());
        }

        @Test
        @DisplayName("currentOffer 为空 → 400 参数校验失败")
        void negotiationScript_blankOffer_shouldReturn400() throws Exception {
            String token = registerUserAndGetToken("negblank");

            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "", "targetSalary": 50.0}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("默认 style → 使用 moderate")
        void negotiationScript_defaultStyle_shouldUseDefault() throws Exception {
            String token = registerUserAndGetToken("negdef");

            mockMvc.perform(post("/api/v1/offerpilot/salary/negotiation-script")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentOffer": "腾讯38k*16"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.openingLine").isNotEmpty());
        }
    }
}
