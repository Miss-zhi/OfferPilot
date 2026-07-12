/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.agent.AgentFactory;
import com.tutorial.offerpilot.dto.chat.ChatRequest;
import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.exception.RateLimitException;
import com.tutorial.offerpilot.service.RateLimitService;
import com.tutorial.offerpilot.service.SearchAnalyticsService;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController Web 层测试")
class ChatControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private AgentFactory agentFactory;

    @Mock
    private SearchAnalyticsService searchAnalyticsService;

    @Mock
    private HarnessAgent agent;

    @Mock
    private Msg msg;

    @InjectMocks
    private ChatController controller;

    private static final String USER_ID = "testuser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        User user = new User(USER_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        // 默认：限流通过、Agent 可用
        lenient().when(agentFactory.getOrCreateAgent(USER_ID)).thenReturn(agent);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== POST /api/v1/offerpilot/chat ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/chat (同步)")
    class SyncChatTests {

        @Test
        @DisplayName("正常对话 → 200 + ChatResponse")
        void chat_shouldReturn200() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(msg.getTextContent()).thenReturn("你好！我是OfferPilot助手，有什么可以帮你？");
            when(agent.call(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Mono.just(msg));

            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "你好，帮我分析一下简历"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.reply").value("你好！我是OfferPilot助手，有什么可以帮你？"))
                    .andExpect(jsonPath("$.data.sessionId").exists());

            verify(rateLimitService).tryAcquireDialogue(USER_ID);
            verify(agentFactory).getOrCreateAgent(USER_ID);
            verify(agent).call(anyString(), nullable(RuntimeContext.class));
        }

        @Test
        @DisplayName("指定 sessionId → 响应中的 sessionId 回传")
        void chat_withSessionId_shouldReturnSameSessionId() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(msg.getTextContent()).thenReturn("回复内容");
            when(agent.call(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Mono.just(msg));

            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "hello", "sessionId": "sess-12345"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value("sess-12345"))
                    .andExpect(jsonPath("$.data.reply").value("回复内容"));
        }

        @Test
        @DisplayName("Agent 返回 null → 兜底消息")
        void chat_nullResponse_shouldReturnFallback() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(agent.call(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Mono.empty());

            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "ping"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reply").value("Agent 未返回有效响应"))
                    .andExpect(jsonPath("$.data.sessionId").exists());
        }

        @Test
        @DisplayName("Agent getTextContent 返回 null → 兜底消息")
        void chat_nullTextContent_shouldReturnFallback() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(msg.getTextContent()).thenReturn(null);
            when(agent.call(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Mono.just(msg));

            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "ping"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reply").value("Agent 未返回有效响应"));
        }

        @Test
        @DisplayName("限流触发 → 429 RateLimitException")
        void chat_rateLimited_shouldReturn429() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(false);

            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "快速请求"}"""))
                    .andExpect(status().is(429))
                    .andExpect(jsonPath("$.code").value(429))
                    .andExpect(jsonPath("$.message").value("对话频率过高，请稍后再试"));

            verify(agentFactory, never()).getOrCreateAgent(anyString());
            verify(agent, never()).call(anyString(), nullable(RuntimeContext.class));
        }

        @Test
        @DisplayName("消息为空 → 400 参数校验失败")
        void chat_blankMessage_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": ""}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(rateLimitService, never()).tryAcquireDialogue(anyString());
            verify(agentFactory, never()).getOrCreateAgent(anyString());
        }

        @Test
        @DisplayName("Agent 异常 → 500 内部错误")
        void chat_agentError_shouldReturn500() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(agent.call(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Mono.error(new RuntimeException("Agent 调用超时")));

            mockMvc.perform(post("/api/v1/offerpilot/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "触发异常"}"""))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
        }
    }

    // ==================== POST /api/v1/offerpilot/chat/stream ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/chat/stream (SSE)")
    class StreamChatTests {

        @Test
        @DisplayName("正常流式 → SSE text/event-stream + delta 事件")
        void chatStream_shouldReturnSseEvents() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(agent.streamEvents(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Flux.just(
                            createDeltaEvent("你好"),
                            createDeltaEvent("！我是"),
                            createDeltaEvent("OfferPilot"),
                            createAgentEndEvent()));

            MvcResult result = mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                            .content("""
                                    {"message": "你好"}"""))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            verify(rateLimitService).tryAcquireDialogue(USER_ID);
            verify(agentFactory).getOrCreateAgent(USER_ID);
            verify(agent).streamEvents(anyString(), nullable(RuntimeContext.class));
        }

        @Test
        @DisplayName("限流触发 → 429（非 SSE）")
        void chatStream_rateLimited_shouldReturn429() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(false);

            mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": "快速请求"}"""))
                    .andExpect(status().is(429))
                    .andExpect(jsonPath("$.code").value(429))
                    .andExpect(jsonPath("$.message").value("对话频率过高，请稍后再试"));

            verify(agentFactory, never()).getOrCreateAgent(anyString());
        }

        @Test
        @DisplayName("消息为空 → 400 参数校验失败")
        void chatStream_blankMessage_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"message": ""}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("流式包含 thinking + tool_call 事件 → 完整透传")
        void chatStream_withThinkingAndToolCall_shouldForwardAllEvents() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(agent.streamEvents(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Flux.just(
                            new ThinkingBlockStartEvent("reply-1", "block-1"),
                            new ThinkingBlockDeltaEvent("reply-1", "block-1", "意图识别：company"),
                            new ToolCallStartEvent("reply-1", "tc-1", "smart_search"),
                            new ToolCallEndEvent("reply-1", "tc-1", "smart_search"),
                            new ThinkingBlockDeltaEvent("reply-1", "block-1", "知识库结果充足"),
                            new ThinkingBlockEndEvent("reply-1", "block-1"),
                            createDeltaEvent("根据知识库检索结果..."),
                            createAgentEndEvent()));

            MvcResult result = mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                            .content("""
                                    {"message": "字节跳动面试题"}""")
                    )
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            verify(agent).streamEvents(anyString(), nullable(RuntimeContext.class));
        }

        @Test
        @DisplayName("无 THINKING_BLOCK 事件时 → 静默跳过，不影响正常流程")
        void chatStream_withoutThinkingEvents_shouldWorkNormally() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            when(agent.streamEvents(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Flux.just(
                            createDeltaEvent("直接回复"),
                            createAgentEndEvent()));

            MvcResult result = mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                            .content("""
                                    {"message": "你好"}""")
                    )
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            verify(agent).streamEvents(anyString(), nullable(RuntimeContext.class));
        }

        @Test
        @DisplayName("客户端断开 → SseEmitter 正确处理")
        void chatStream_clientDisconnect_shouldHandleCleanly() throws Exception {
            when(rateLimitService.tryAcquireDialogue(USER_ID)).thenReturn(true);
            // Flux empty: 模拟无事件场景
            when(agent.streamEvents(anyString(), nullable(RuntimeContext.class)))
                    .thenReturn(Flux.empty());

            MvcResult result = mockMvc.perform(post("/api/v1/offerpilot/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM_VALUE)
                            .content("""
                                    {"message": "ping"}"""))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            verify(agent).streamEvents(anyString(), nullable(RuntimeContext.class));
        }
    }

    // ==================== 辅助方法 ====================

    private AgentEvent createDeltaEvent(String text) {
        return new TextBlockDeltaEvent("reply-1", "block-1", text);
    }

    private AgentEvent createAgentEndEvent() {
        return new AgentEndEvent("reply-1");
    }
}
