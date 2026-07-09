/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 模型调用成本控制中间件。
 * 洋葱模式 hook:
 * <ul>
 *   <li>onAgent — 记录 Agent 整体调用开始/结束，重置计数器</li>
 *   <li>onModelCall — 拦截模型调用，监控 Token 消耗和调用次数，超阈值拒绝</li>
 * </ul>
 */
@Slf4j
public class CostControlMiddleware implements MiddlewareBase {

    /** 单次会话最大模型调用次数 */
    private static final int MAX_CALLS_PER_SESSION = 50;

    /** 单次会话最大 Token 消耗（输入+输出） */
    private static final long MAX_TOKENS_PER_SESSION = 200_000L;

    /** 单次模型调用最大 Token 消耗 */
    private static final long MAX_TOKENS_PER_CALL = 16_000L;

    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicInteger totalTokens = new AtomicInteger(0);

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        callCount.set(0);
        totalTokens.set(0);
        log.info("[CostControl] Agent call started: agent={}, session={}, messages={}",
                agent.getName(), ctx.getSessionId(), input.msgs().size());
        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "[CostControl] Agent call finished: calls={}, totalTokens={}",
                        callCount.get(), totalTokens.get()));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        int currentCall = callCount.incrementAndGet();
        log.info("[CostControl] Model call #{}: model={}, messages={}, tools={}",
                currentCall,
                input.model().getClass().getSimpleName(),
                input.messages().size(),
                input.tools().size());

        // 调用次数超限
        if (currentCall > MAX_CALLS_PER_SESSION) {
            log.warn("[CostControl] Rejecting model call #{} — exceeded max calls per session ({})",
                    currentCall, MAX_CALLS_PER_SESSION);
            return Flux.error(new CostLimitExceededException(
                    "Model call limit exceeded: " + MAX_CALLS_PER_SESSION));
        }

        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof ModelCallEndEvent end) {
                        ChatUsage usage = end.getUsage();
                        if (usage != null) {
                            int callTokens = usage.getInputTokens() + usage.getOutputTokens();
                            int sessionTokens = totalTokens.addAndGet(callTokens);
                            log.info("[CostControl] Model call #{} tokens: input={}, output={}, total={}, sessionTotal={}",
                                    currentCall, usage.getInputTokens(), usage.getOutputTokens(),
                                    callTokens, sessionTokens);

                            // 单次 Token 超限
                            if (callTokens > MAX_TOKENS_PER_CALL) {
                                log.warn("[CostControl] Call #{} exceeded per-call token limit: {} > {}",
                                        currentCall, callTokens, MAX_TOKENS_PER_CALL);
                            }

                            // 会话 Token 超限
                            if (sessionTokens > MAX_TOKENS_PER_SESSION) {
                                log.warn("[CostControl] Session exceeded token limit: {} > {}",
                                        sessionTokens, MAX_TOKENS_PER_SESSION);
                            }
                        }
                    }
                });
    }

    /**
     * 成本超限异常，由 onAgent 调用方捕获处理。
     */
    public static class CostLimitExceededException extends RuntimeException {
        public CostLimitExceededException(String message) {
            super(message);
        }
    }
}
