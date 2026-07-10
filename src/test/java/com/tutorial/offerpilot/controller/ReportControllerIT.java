/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ReportController 集成测试")
class ReportControllerIT extends AbstractControllerIT {

    // ==================== GET /api/v1/offerpilot/reports ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/reports")
    class ListReportsTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/offerpilot/reports"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("认证用户 → 200 + 报告列表（可为空）")
        void authenticated_shouldReturn200() throws Exception {
            String token = registerUserAndGetToken("rptuser");

            mockMvc.perform(get("/api/v1/offerpilot/reports")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ==================== GET /api/v1/offerpilot/reports/{reportId} ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/reports/{reportId}")
    class GetReportTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/offerpilot/reports/rpt-001"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("不存在的报告 → 404 Not Found")
        void nonExistentReport_shouldReturn404() throws Exception {
            String token = registerUserAndGetToken("rptgetuser");

            mockMvc.perform(get("/api/v1/offerpilot/reports/rpt-nonexistent")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== POST /api/v1/offerpilot/reports ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/reports")
    class GenerateReportTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\": \"sess-001\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("不存在的 session → 404 Not Found")
        void nonExistentSession_shouldReturn404() throws Exception {
            String token = registerUserAndGetToken("rptgenuser");

            mockMvc.perform(post("/api/v1/offerpilot/reports")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\": \"sess-nonexistent\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}
