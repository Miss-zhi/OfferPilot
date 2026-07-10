/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ProgressController 集成测试")
class ProgressControllerIT extends AbstractControllerIT {

    // ==================== GET /api/v1/offerpilot/progress ====================

    @Nested
    @DisplayName("GET /api/v1/offerpilot/progress")
    class GetProgressTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/offerpilot/progress"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("认证用户 → 200 + 进度数据")
        void authenticated_shouldReturn200WithProgress() throws Exception {
            String token = registerUserAndGetToken("proguser");

            mockMvc.perform(get("/api/v1/offerpilot/progress")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.period").value("month"))
                    .andExpect(jsonPath("$.data.interviewCount").value(0))
                    .andExpect(jsonPath("$.data.scoreTrend").isArray())
                    .andExpect(jsonPath("$.data.knowledgeMastery").isMap())
                    .andExpect(jsonPath("$.data.studyPlan.completed").value(0))
                    .andExpect(jsonPath("$.data.studyPlan.total").value(0));
        }

        @Test
        @DisplayName("指定 range=week → 返回 period=week")
        void customRange_shouldReflectInResponse() throws Exception {
            String token = registerUserAndGetToken("rangeuser");

            mockMvc.perform(get("/api/v1/offerpilot/progress")
                            .param("range", "week")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.period").value("week"));
        }

        @Test
        @DisplayName("无效 Token → 401 Unauthorized")
        void invalidToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/offerpilot/progress")
                            .header("Authorization", "Bearer invalid.token.here"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
