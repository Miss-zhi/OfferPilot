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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/v1/offerpilot/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RateLimitService rateLimitService;
    private final AgentFactory agentFactory;
    private final SearchAnalyticsService searchAnalyticsService;

    /** 防止同一用户并发 SSE 请求 */
    private final ConcurrentHashMap<String, Boolean> activeStreams = new ConcurrentHashMap<>();

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

        // 防止同一用户并发 SSE 流
        if (activeStreams.putIfAbsent(userId, Boolean.TRUE) != null) {
            log.warn("Rejecting concurrent stream request for userId={}", userId);
            SseEmitter rejectEmitter = new SseEmitter();
            try {
                rejectEmitter.send(SseEmitter.event().name("error").data("请等待当前对话完成后再发送新消息"));
                rejectEmitter.complete();
            } catch (IOException ignored) {}
            return rejectEmitter;
        }

        HarnessAgent agent = agentFactory.getOrCreateAgent(userId);
        String sessionId = resolveSessionId(request.getSessionId());
        RuntimeContext ctx = new RuntimeContext.Builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();

        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        AtomicBoolean completed = new AtomicBoolean(false);

        // 确保流结束时释放并发锁（幂等操作，可安全多次调用）
        Runnable releaseStream = () -> activeStreams.remove(userId);

        // 安全发送 SSE 事件 — 捕获 IOException 避免 "response already committed" 级联异常
        java.util.function.BiConsumer<String, Object> safeSend = (eventName, data) -> {
            try {
                if (!completed.get()) {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                }
            } catch (IOException e) {
                log.debug("SSE send failed (client disconnected?): eventName={}", eventName);
                completed.set(true);
            } catch (IllegalStateException e) {
                // response already committed — 忽略
                log.debug("SSE send skipped (response already committed): eventName={}", eventName);
                completed.set(true);
            }
        };

        Flux<AgentEvent> eventFlux = agent.streamEvents(request.getMessage(), ctx);

        // 先发送 sessionId 事件，让前端能够保持会话连续性
        safeSend.accept("session", sessionId);

        eventFlux
                .doOnNext(event -> {
                    if (completed.get()) return;
                    if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA
                            && event instanceof TextBlockDeltaEvent delta) {
                        safeSend.accept("delta", delta.getDelta());
                    } else if (event.getType() == AgentEventType.AGENT_END) {
                        // 异步记录对话反馈
                        searchAnalyticsService.recordFeedback(userId, request.getMessage(),
                                "chat_stream", "agent", 1, null, sessionId);
                        if (completed.compareAndSet(false, true)) {
                            safeSend.accept("done", "completed");
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("Agent stream error: userId={}", userId, error);
                    if (completed.compareAndSet(false, true)) {
                        safeSend.accept("error", error.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // 兜底：若 Flux 结束但未触发 AGENT_END 事件，确保客户端收到完成信号
                    if (completed.compareAndSet(false, true)) {
                        searchAnalyticsService.recordFeedback(userId, request.getMessage(),
                                "chat_stream", "agent", 1, null, sessionId);
                        safeSend.accept("done", "completed");
                    }
                })
                .doOnCancel(() -> {
                    // 客户端主动断开连接（如关闭浏览器、中止请求）
                    log.info("SSE stream cancelled by client: userId={}", userId);
                    completed.set(true);
                })
                .doFinally(signal -> {
                    // 无论何种终止信号（complete/error/cancel），一定释放锁 + 关闭 emitter
                    releaseStream.run();
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // emitter 可能已经完成或出错 — 忽略
                    }
                })
                .subscribe();

        // SseEmitter 超时/错误回调 — 确保锁释放
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout: userId={}", userId);
            completed.set(true);
            releaseStream.run();
        });
        emitter.onError(ex -> {
            log.warn("SSE emitter error: userId={}", userId, ex);
            completed.set(true);
            releaseStream.run();
        });
        emitter.onCompletion(releaseStream::run);

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
