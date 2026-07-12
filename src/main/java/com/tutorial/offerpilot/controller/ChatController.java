/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.agent.AgentFactory;
import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.chat.*;
import com.tutorial.offerpilot.entity.ChatMessage;
import com.tutorial.offerpilot.exception.RateLimitException;
import com.tutorial.offerpilot.service.ChatHistoryService;
import com.tutorial.offerpilot.service.RateLimitService;
import com.tutorial.offerpilot.service.SearchAnalyticsService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/api/v1/offerpilot/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RateLimitService rateLimitService;
    private final AgentFactory agentFactory;
    private final SearchAnalyticsService searchAnalyticsService;
    private final ChatHistoryService chatHistoryService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 防止同一用户并发 SSE 请求 */
    private final ConcurrentHashMap<String, Boolean> activeStreams = new ConcurrentHashMap<>();

    /** 等待 HITL 确认的 emitter（userId → emitter），确认后复用该 emitter 继续推送 */
    private final ConcurrentHashMap<String, SseEmitter> pendingConfirmEmitters = new ConcurrentHashMap<>();

    /** 等待 HITL 确认的 agent/ctx 信息 */
    private final ConcurrentHashMap<String, ConfirmContext> pendingConfirmCtxs = new ConcurrentHashMap<>();

    /** AI 响应内容缓冲区，跨 HITL 边界传递时使用字符串副本（不可变） */
    private record AiMessageBuffer(
            String content,
            String thinking,
            List<String> toolCalls
    ) {
        AiMessageBuffer() { this("", "", new ArrayList<>()); }

        AiMessageBuffer appendContent(String delta) {
            return new AiMessageBuffer(content + delta, thinking, toolCalls);
        }
        AiMessageBuffer appendThinking(String delta) {
            return new AiMessageBuffer(content, thinking + delta, toolCalls);
        }
        AiMessageBuffer addToolCall(String name) {
            List<String> updated = new ArrayList<>(toolCalls);
            updated.add(name);
            return new AiMessageBuffer(content, thinking, updated);
        }
    }

    private record ConfirmContext(HarnessAgent agent, RuntimeContext ctx, String sessionId, AiMessageBuffer buffer) {}

    /** 保存 AI 消息到数据库 — chatStream / confirmTools / doOnComplete 三处复用 */
    private void saveAiMessage(String sessionId, String userId, AiMessageBuffer buffer) {
        String content = buffer.content();
        if (content == null || content.isBlank()) {
            return;
        }
        try {
            MessageSaveRequest req = new MessageSaveRequest();
            req.setRole("AI");
            req.setContent(content);
            String thinking = buffer.thinking();
            if (thinking != null && !thinking.isEmpty()) {
                req.setThinkingContent(thinking);
            }
            List<String> toolCalls = buffer.toolCalls();
            if (toolCalls != null && !toolCalls.isEmpty()) {
                req.setToolCalls(objectMapper.writeValueAsString(toolCalls));
            }
            chatHistoryService.saveMessage(sessionId, userId, req);
            log.info("Saved AI message: sessionId={}, contentLen={}", sessionId, content.length());
        } catch (Exception e) {
            log.warn("Failed to save AI message: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

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
            @AuthenticationPrincipal UserDetails currentUser,
            HttpServletResponse response) {

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

        String sessionId = resolveSessionId(request.getSessionId());

        // 保存用户消息到 DB（自动创建 session 若不存在）
        try {
            MessageSaveRequest saveReq = new MessageSaveRequest();
            saveReq.setRole("USER");
            saveReq.setContent(request.getMessage());
            chatHistoryService.saveMessage(sessionId, userId, saveReq);
        } catch (Exception e) {
            log.warn("Failed to save user message: sessionId={}, error={}", sessionId, e.getMessage());
        }

        // AI 响应缓冲区，流式累积后在 AGENT_END/doOnComplete 保存
        java.util.concurrent.atomic.AtomicReference<AiMessageBuffer> aiBufferRef =
                new java.util.concurrent.atomic.AtomicReference<>(new AiMessageBuffer());

        HarnessAgent agent = agentFactory.getOrCreateAgent(userId);
        RuntimeContext ctx = new RuntimeContext.Builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();

        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean hasTextContent = new AtomicBoolean(false);
        AtomicBoolean confirmPending = new AtomicBoolean(false); // HITL 确认等待标志

        // 确保流结束时释放并发锁
        Runnable releaseStream = () -> activeStreams.remove(userId);

        // 安全发送 SSE 事件 — 捕获 IOException 避免级联异常
        java.util.function.BiConsumer<String, Object> safeSend = (eventName, data) -> {
            try {
                if (!completed.get()) {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                }
            } catch (IOException e) {
                log.debug("SSE send failed (client disconnected?): eventName={}", eventName);
                completed.set(true);
            }
        };

        Flux<AgentEvent> eventFlux = agent.streamEvents(request.getMessage(), ctx);

        // 先发送 sessionId — SseEmitter.send() 是同步的，在 subscribe 前发送没有问题
        safeSend.accept("session", sessionId);

        eventFlux
                .doOnNext(event -> {
                    if (completed.get()) return;
                    if (event.getType() == AgentEventType.THINKING_BLOCK_START) {
                        safeSend.accept("thinking_start", "{}");
                    } else if (event.getType() == AgentEventType.THINKING_BLOCK_DELTA
                            && event instanceof ThinkingBlockDeltaEvent thinkingDelta) {
                        aiBufferRef.updateAndGet(b -> b.appendThinking(thinkingDelta.getDelta()));
                        safeSend.accept("thinking", thinkingDelta.getDelta());
                    } else if (event.getType() == AgentEventType.THINKING_BLOCK_END) {
                        safeSend.accept("thinking_end", "{}");
                    } else if (event.getType() == AgentEventType.TOOL_CALL_START
                            && event instanceof ToolCallStartEvent toolCall) {
                        log.debug("Tool call start: tool={}, userId={}", toolCall.getToolCallName(), userId);
                        aiBufferRef.updateAndGet(b -> b.addToolCall(toolCall.getToolCallName()));
                        safeSend.accept("tool_call", toolCall.getToolCallName());
                    } else if (event.getType() == AgentEventType.TOOL_CALL_END) {
                        safeSend.accept("tool_call_end", "{}");
                    } else if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                            && event instanceof RequireUserConfirmEvent confirmEvent) {
                        // HITL: Agent 需要用户确认工具调用
                        log.info("REQUIRE_USER_CONFIRM: userId={}, tools={}", userId,
                                confirmEvent.getToolCalls().stream()
                                        .map(t -> t.getName() + "(" + t.getId() + ")")
                                        .toList());
                        confirmPending.set(true);
                        pendingConfirmEmitters.put(userId, emitter);
                        pendingConfirmCtxs.put(userId, new ConfirmContext(agent, ctx, sessionId, aiBufferRef.get()));
                        String confirmJson = buildConfirmJson(sessionId, confirmEvent.getToolCalls());
                        safeSend.accept("confirm_required", confirmJson);
                    } else if (event.getType() == AgentEventType.REQUEST_STOP) {
                        log.debug("REQUEST_STOP received: userId={}", userId);
                    } else if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA
                            && event instanceof TextBlockDeltaEvent delta) {
                        hasTextContent.set(true);
                        aiBufferRef.updateAndGet(b -> b.appendContent(delta.getDelta()));
                        safeSend.accept("delta", delta.getDelta());
                    } else if (event.getType() == AgentEventType.AGENT_END) {
                        saveAiMessage(sessionId, userId, aiBufferRef.get());
                        log.info("AGENT_END received, sending done: userId={}", userId);
                        searchAnalyticsService.recordFeedback(userId, request.getMessage(),
                                "chat_stream", "agent", 1, null, sessionId);
                        // 必须在 compareAndSet 之前发送 done，否则 safeSend 会因为 completed=true 而跳过
                        safeSend.accept("done", "completed");
                        if (completed.compareAndSet(false, true)) {
                            try {
                                response.flushBuffer();
                            } catch (IOException e) {
                                log.debug("flushBuffer failed after done: {}", e.getMessage());
                            }
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("Agent stream error: userId={}", userId, error);
                    pendingConfirmEmitters.remove(userId);
                    pendingConfirmCtxs.remove(userId);
                    if (completed.compareAndSet(false, true)) {
                        safeSend.accept("error", error.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // HITL 确认等待中 — 不发送 done，保持 emitter 存活
                    if (confirmPending.get()) {
                        log.info("Flux onComplete (confirm pending, keeping emitter alive): userId={}", userId);
                        return;
                    }
                    // 兜底：若 Flux 结束但未触发 AGENT_END
                    if (completed.compareAndSet(false, true)) {
                        saveAiMessage(sessionId, userId, aiBufferRef.get());
                        log.info("Flux onComplete (fallback done): userId={}, hasText={}", userId, hasTextContent.get());
                        searchAnalyticsService.recordFeedback(userId, request.getMessage(),
                                "chat_stream", "agent", hasTextContent.get() ? 1 : 0, null, sessionId);
                        if (!hasTextContent.get()) {
                            safeSend.accept("delta", "抱歉，处理您的请求时出现了问题，请稍后重试。");
                        }
                        // completed 已为 true，直接用 emitter.send 发送 done
                        try {
                            emitter.send(SseEmitter.event().name("done").data("completed"));
                        } catch (IOException ignored) {}
                        try {
                            response.flushBuffer();
                        } catch (IOException e) {
                            log.debug("flushBuffer failed after fallback done: {}", e.getMessage());
                        }
                    }
                })
                .doOnCancel(() -> {
                    log.info("SSE stream cancelled by client: userId={}", userId);
                    pendingConfirmEmitters.remove(userId);
                    pendingConfirmCtxs.remove(userId);
                    completed.set(true);
                })
                .doFinally(signal -> {
                    releaseStream.run();
                    // HITL 确认等待中 — 不关闭 emitter
                    if (confirmPending.get()) {
                        log.debug("doFinally: emitter kept alive for HITL confirm, userId={}", userId);
                        return;
                    }
                    // 安全网：若此前未发送 done，补发
                    if (completed.compareAndSet(false, true)) {
                        if (!hasTextContent.get()) {
                            try {
                                emitter.send(SseEmitter.event().name("delta").data("抱歉，处理您的请求时出现了问题，请稍后重试。"));
                            } catch (Exception ignored) {}
                        }
                        try {
                            emitter.send(SseEmitter.event().name("done").data("completed"));
                        } catch (Exception ignored) {}
                    }
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                })
                .subscribe();

        // SseEmitter 超时/错误回调
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

    /**
     * HITL 确认端点 — 用户批准/拒绝工具调用后，恢复 Agent 流。
     */
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirmTools(
            @RequestBody @Valid ConfirmRequest confirmRequest,
            @AuthenticationPrincipal UserDetails currentUser,
            HttpServletResponse response) {

        String userId = getUserId(currentUser);
        log.info("Confirm request: userId={}, sessionId={}, items={}",
                userId, confirmRequest.getSessionId(), confirmRequest.getConfirmations().size());

        SseEmitter emitter = pendingConfirmEmitters.remove(userId);
        ConfirmContext confirmCtx = pendingConfirmCtxs.remove(userId);

        if (emitter == null || confirmCtx == null) {
            log.warn("No pending confirmation found for userId={}", userId);
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().name("error")
                        .data("没有等待确认的工具调用，请重新发送消息"));
                errorEmitter.complete();
            } catch (IOException ignored) {}
            return errorEmitter;
        }

        HarnessAgent agent = confirmCtx.agent();
        RuntimeContext ctx = confirmCtx.ctx();
        String sessionId = confirmRequest.getSessionId();

        // 从 ConfirmContext 恢复 HITL 之前已累积的缓冲区
        java.util.concurrent.atomic.AtomicReference<AiMessageBuffer> aiBufferRef =
                new java.util.concurrent.atomic.AtomicReference<>(confirmCtx.buffer());

        // 构建 ConfirmResult 列表
        List<ConfirmResult> confirmResults = confirmRequest.getConfirmations().stream()
                .map(item -> {
                    ToolUseBlock toolCall = ToolUseBlock.builder()
                            .id(item.getToolCallId())
                            .name(item.getToolCallName())
                            .input(item.getToolCallInput() != null
                                    ? item.getToolCallInput()
                                    : Map.of())
                            .build();
                    return new ConfirmResult(item.isConfirmed(), toolCall);
                })
                .toList();

        // 构建恢复消息（纯元数据，无文本内容）
        UserMessage resumeMsg = UserMessage.builder()
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmResults))
                .build();

        log.info("Resuming agent with {} confirm result(s): userId={}",
                confirmResults.size(), userId);

        // 复用已有 emitter，通过 completed 重置状态
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean hasTextContent = new AtomicBoolean(false);

        java.util.function.BiConsumer<String, Object> safeSend = (eventName, data) -> {
            try {
                if (!completed.get()) {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                }
            } catch (IOException e) {
                log.debug("SSE send failed (client disconnected?): eventName={}", eventName);
                completed.set(true);
            }
        };

        Flux<AgentEvent> eventFlux = agent.streamEvents(resumeMsg, ctx);

        eventFlux
                .doOnNext(event -> {
                    if (completed.get()) return;
                    if (event.getType() == AgentEventType.THINKING_BLOCK_START) {
                        safeSend.accept("thinking_start", "{}");
                    } else if (event.getType() == AgentEventType.THINKING_BLOCK_DELTA
                            && event instanceof ThinkingBlockDeltaEvent thinkingDelta) {
                        aiBufferRef.updateAndGet(b -> b.appendThinking(thinkingDelta.getDelta()));
                        safeSend.accept("thinking", thinkingDelta.getDelta());
                    } else if (event.getType() == AgentEventType.THINKING_BLOCK_END) {
                        safeSend.accept("thinking_end", "{}");
                    } else if (event.getType() == AgentEventType.TOOL_CALL_START
                            && event instanceof ToolCallStartEvent toolCall) {
                        aiBufferRef.updateAndGet(b -> b.addToolCall(toolCall.getToolCallName()));
                        safeSend.accept("tool_call", toolCall.getToolCallName());
                    } else if (event.getType() == AgentEventType.TOOL_CALL_END) {
                        safeSend.accept("tool_call_end", "{}");
                    } else if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                            && event instanceof RequireUserConfirmEvent confirmEvent) {
                        log.info("REQUIRE_USER_CONFIRM (resumed): userId={}, tools={}", userId,
                                confirmEvent.getToolCalls().stream()
                                        .map(t -> t.getName() + "(" + t.getId() + ")")
                                        .toList());
                        String confirmJson = buildConfirmJson(sessionId, confirmEvent.getToolCalls());
                        safeSend.accept("confirm_required", confirmJson);
                        // 再次存储 emitter/ctx 以各后续确认
                        pendingConfirmEmitters.put(userId, emitter);
                        pendingConfirmCtxs.put(userId, new ConfirmContext(agent, ctx, sessionId, aiBufferRef.get()));
                    } else if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA
                            && event instanceof TextBlockDeltaEvent delta) {
                        hasTextContent.set(true);
                        aiBufferRef.updateAndGet(b -> b.appendContent(delta.getDelta()));
                        safeSend.accept("delta", delta.getDelta());
                    } else if (event.getType() == AgentEventType.AGENT_END) {
                        saveAiMessage(sessionId, userId, aiBufferRef.get());
                        log.info("AGENT_END received (resumed): userId={}", userId);
                        searchAnalyticsService.recordFeedback(userId, resumeMsg.getTextContent(),
                                "chat_stream", "agent", 1, null, sessionId);
                        // 必须在 compareAndSet 之前发送 done，否则 safeSend 会因为 completed=true 而跳过
                        safeSend.accept("done", "completed");
                        if (completed.compareAndSet(false, true)) {
                            try {
                                response.flushBuffer();
                            } catch (IOException e) {
                                log.debug("flushBuffer failed after done: {}", e.getMessage());
                            }
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("Agent stream error (resumed): userId={}", userId, error);
                    if (completed.compareAndSet(false, true)) {
                        safeSend.accept("error", error.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    if (completed.compareAndSet(false, true)) {
                        saveAiMessage(sessionId, userId, aiBufferRef.get());
                        log.info("Flux onComplete (resumed fallback): userId={}, hasText={}",
                                userId, hasTextContent.get());
                        if (!hasTextContent.get()) {
                            safeSend.accept("delta", "抱歉，处理您的请求时出现了问题，请稍后重试。");
                        }
                        // completed 已为 true，直接用 emitter.send 发送 done
                        try {
                            emitter.send(SseEmitter.event().name("done").data("completed"));
                        } catch (IOException ignored) {}
                        try {
                            response.flushBuffer();
                        } catch (IOException e) {
                            log.debug("flushBuffer failed after fallback done: {}", e.getMessage());
                        }
                    }
                })
                .doOnCancel(() -> {
                    log.info("SSE stream cancelled by client (resumed): userId={}", userId);
                    completed.set(true);
                })
                .doFinally(signal -> {
                    if (completed.compareAndSet(false, true)) {
                        if (!hasTextContent.get()) {
                            try {
                                emitter.send(SseEmitter.event().name("delta")
                                        .data("抱歉，处理您的请求时出现了问题，请稍后重试。"));
                            } catch (Exception ignored) {}
                        }
                        try {
                            emitter.send(SseEmitter.event().name("done").data("completed"));
                        } catch (Exception ignored) {}
                    }
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                })
                .subscribe();

        return emitter;
    }

    // ============================================================
    // 会话历史管理端点
    // ============================================================

    /** 获取当前用户会话列表 */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionListItem>> listSessions(
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("List sessions request: userId={}", userId);
        List<SessionListItem> sessions = chatHistoryService.listSessions(userId);
        return ApiResponse.success(sessions);
    }

    /** 创建新会话 */
    @PostMapping("/sessions")
    public ApiResponse<SessionListItem> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Create session request: userId={}, activeFunction={}", userId, request.getActiveFunction());
        SessionListItem session = chatHistoryService.createSession(userId, request.getActiveFunction());
        return ApiResponse.success(session);
    }

    /** 删除会话（级联删除所有消息） */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Delete session request: userId={}, sessionId={}", userId, sessionId);
        chatHistoryService.deleteSession(sessionId, userId);
        return ApiResponse.success(null);
    }

    /** 重命名会话 */
    @PatchMapping("/sessions/{sessionId}")
    public ApiResponse<Void> renameSession(
            @PathVariable String sessionId,
            @RequestBody @Valid RenameSessionRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Rename session request: userId={}, sessionId={}, title={}", userId, sessionId, request.getTitle());
        chatHistoryService.renameSession(sessionId, request.getTitle(), userId);
        return ApiResponse.success(null);
    }

    /** 获取会话全部消息 */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<MessageListItem>> getMessages(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Get messages request: userId={}, sessionId={}", userId, sessionId);
        List<MessageListItem> messages = chatHistoryService.getMessages(sessionId, userId);
        return ApiResponse.success(messages);
    }

    /** 保存一条消息 */
    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatMessage> saveMessage(
            @PathVariable String sessionId,
            @RequestBody @Valid MessageSaveRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Save message request: userId={}, sessionId={}, role={}", userId, sessionId, request.getRole());
        ChatMessage msg = chatHistoryService.saveMessage(sessionId, userId, request);
        return ApiResponse.success(msg);
    }

    /** 全文搜索消息 */
    @GetMapping("/sessions/search")
    public ApiResponse<List<SearchResultItem>> searchSessions(
            @RequestParam("q") String keyword,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Search sessions request: userId={}, keyword={}", userId, keyword);
        List<SearchResultItem> results = chatHistoryService.searchMessages(userId, keyword);
        return ApiResponse.success(results);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }

    private String resolveSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        return "sess-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 构建发送给前端的 confirm_required JSON */
    private String buildConfirmJson(String sessionId, List<ToolUseBlock> toolCalls) {
        StringBuilder json = new StringBuilder("{\"sessionId\":\"");
        json.append(escapeJson(sessionId));
        json.append("\",\"toolCalls\":[");
        for (int i = 0; i < toolCalls.size(); i++) {
            if (i > 0) json.append(",");
            var tc = toolCalls.get(i);
            json.append("{\"id\":\"").append(escapeJson(tc.getId())).append("\"");
            json.append(",\"name\":\"").append(escapeJson(tc.getName())).append("\"");
            json.append(",\"input\":").append(toJson(tc.getInput()));
            json.append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val instanceof String s) {
                sb.append("\"").append(escapeJson(s)).append("\"");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else if (val instanceof List<?> list) {
                sb.append("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    Object item = list.get(i);
                    if (item instanceof String s) {
                        sb.append("\"").append(escapeJson(s)).append("\"");
                    } else {
                        sb.append(item);
                    }
                }
                sb.append("]");
            } else if (val instanceof Map) {
                sb.append(toJson((Map<String, Object>) val));
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(val))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
