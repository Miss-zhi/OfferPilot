/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.agent.AgentFactory;
import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.chat.ChatRequest;
import com.tutorial.offerpilot.dto.chat.ChatResponse;
import com.tutorial.offerpilot.exception.RateLimitException;
import com.tutorial.offerpilot.service.RateLimitService;
import com.tutorial.offerpilot.service.SearchAnalyticsService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/offerpilot/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RateLimitService rateLimitService;
    private final AgentFactory agentFactory;
    private final SearchAnalyticsService searchAnalyticsService;

    /** 同步对话 */
    @PostMapping
    public ApiResponse<ChatResponse> chat(
            @RequestBody @Valid ChatRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        String userId = getUserId(currentUser);
        log.info("Chat request: userId={}, sessionId={}", userId, request.getSessionId());

        if (!rateLimitService.tryAcquireDialogue(userId)) {
            throw new RateLimitException("对话频率过高，请稍后再试");
        }

        HarnessAgent agent = agentFactory.getOrCreateAgent(userId);
        String sessionId = resolveSessionId(request.getSessionId());
        RuntimeContext ctx = new RuntimeContext.Builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();

        io.agentscope.core.message.Msg msg = agent.call(request.getMessage(), ctx).block();
        String reply = msg != null ? msg.getTextContent() : null;
        ChatResponse response = new ChatResponse(
                reply != null ? reply : "Agent 未返回有效响应", sessionId);

        // 异步记录对话反馈（helpful 默认为 null，待用户后续评分）
        searchAnalyticsService.recordFeedback(userId, request.getMessage(), "chat",
                "agent", reply != null ? 1 : 0, null, sessionId);

        return ApiResponse.success(response);
    }

    /**
     * SSE 流式对话 — Agent 调用 + SseEmitter 流式输出。
     * 通过 Server-Sent Events 实时推送 Agent 生成的文本增量。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestBody @Valid ChatRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        String userId = getUserId(currentUser);
        log.info("Chat stream request: userId={}, sessionId={}", userId, request.getSessionId());

        if (!rateLimitService.tryAcquireDialogue(userId)) {
            throw new RateLimitException("对话频率过高，请稍后再试");
        }

        HarnessAgent agent = agentFactory.getOrCreateAgent(userId);
        String sessionId = resolveSessionId(request.getSessionId());
        RuntimeContext ctx = new RuntimeContext.Builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();

        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        Flux<AgentEvent> eventFlux = agent.streamEvents(request.getMessage(), ctx);

        eventFlux
                .doOnNext(event -> {
                    try {
                        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA
                                && event instanceof TextBlockDeltaEvent delta) {
                            emitter.send(SseEmitter.event()
                                    .name("delta")
                                    .data(delta.getDelta()));
                        } else if (event.getType() == AgentEventType.AGENT_END) {
                            // 异步记录对话反馈
                            searchAnalyticsService.recordFeedback(userId, request.getMessage(),
                                    "chat_stream", "agent", 1, null, sessionId);
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("completed"));
                            emitter.complete();
                        }
                    } catch (IOException e) {
                        log.warn("SSE send error: userId={}", userId, e);
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(error -> {
                    log.error("Agent stream error: userId={}", userId, error);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(error.getMessage()));
                    } catch (IOException ignored) {
                        // ignore
                    }
                    emitter.completeWithError(error);
                })
                .doOnComplete(() -> {
                    try {
                        // 异步记录对话反馈（兜底：若未触发 AGENT_END 事件）
                        searchAnalyticsService.recordFeedback(userId, request.getMessage(),
                                "chat_stream", "agent", 1, null, sessionId);
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("completed"));
                        emitter.complete();
                    } catch (IOException ignored) {
                        emitter.complete();
                    }
                })
                .subscribe();

        return emitter;
    }

    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        return "sess-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
