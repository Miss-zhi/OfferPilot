/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ChatController 集成测试")
class ChatControllerIT extends AbstractControllerIT {

    // ==================== POST /api/v1/offerpilot/chat ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/chat")
    class ChatTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "你好", "sessionId": "sess-001"}"""))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("无效 Token → 401 Unauthorized")
        void invalidToken_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .header("Authorization", "Bearer invalid.token.here")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "你好"}"""))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== POST /api/v1/offerpilot/chat/stream ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/chat/stream")
    class StreamTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "你好", "sessionId": "sess-001"}"""))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("无效 Token → 401 Unauthorized")
        void invalidToken_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .header("Authorization", "Bearer invalid.token.here")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "你好"}"""))
                    .andExpect(status().isUnauthorized());
        }
    }
}
