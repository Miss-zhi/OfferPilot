/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.ChatUsage;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Token 消耗监控中间件 + 工具调用 Trace。
 * 洋葱模式 hook：
 * <ul>
 *   <li>onReasoning — 记录推理阶段开始，计算上下文消息数</li>
 *   <li>onModelCall — 提取 ChatUsage 并累加 Token 统计</li>
 *   <li>onActing — 记录工具调用参数、耗时、状态（配合 Studio 实时监控）</li>
 * </ul>
 */
@Slf4j
public class TokenMonitorMiddleware implements MiddlewareBase {

    private final AtomicInteger reasoningCount = new AtomicInteger(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicInteger totalToolCalls = new AtomicInteger(0);

    /** 工具调用开始时间记录：toolCallId → startTimeMs */
    private final Map<String, Long> toolCallStartTimes = new ConcurrentHashMap<>();

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        int round = reasoningCount.incrementAndGet();
        int msgCount = input.messages() != null ? input.messages().size() : 0;
        int toolCount = input.tools() != null ? input.tools().size() : 0;
        log.info("[TokenMonitor] Reasoning #{}: agent={}, session={}, messages={}, tools={}",
                round, agent.getName(), ctx.getSessionId(), msgCount, toolCount);
        return next.apply(input)
                .doOnComplete(() -> log.info("[TokenMonitor] Reasoning #{} completed", round));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        long startMs = System.currentTimeMillis();
        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof ModelCallEndEvent end) {
                        long elapsed = System.currentTimeMillis() - startMs;
                        ChatUsage usage = end.getUsage();
                        if (usage != null) {
                            long prompt = totalPromptTokens.addAndGet(usage.getInputTokens());
                            long completion = totalCompletionTokens.addAndGet(usage.getOutputTokens());
                            log.info("[TokenMonitor] Model call finished in {}ms: " +
                                            "input_tokens={}, output_tokens={}, " +
                                            "session_prompt_tokens={}, session_completion_tokens={}",
                                    elapsed,
                                    usage.getInputTokens(), usage.getOutputTokens(),
                                    prompt, completion);
                        }
                    }
                });
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        // 记录本轮工具调用的参数（从 ActingInput.toolCalls() 提取）
        List<ToolUseBlock> toolUseBlocks = input.toolCalls();
        if (toolUseBlocks != null) {
            for (ToolUseBlock block : toolUseBlocks) {
                toolCallStartTimes.put(block.getId(), System.currentTimeMillis());
                log.info("[TokenMonitor] Tool call started: id={}, name={}, params={}",
                        block.getId(), block.getName(), block.getInput());
            }
        }

        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof ToolCallStartEvent start) {
                        int callIndex = totalToolCalls.incrementAndGet();
                        // 若 ActingInput 中无此 toolCallId，这里补充记录
                        toolCallStartTimes.putIfAbsent(start.getToolCallId(), System.currentTimeMillis());
                        log.info("[TokenMonitor] Tool call #{}: name={}, toolCallId={}",
                                callIndex, start.getToolCallName(), start.getToolCallId());
                    }
                })
                .doOnComplete(() -> {
                    // 遍历本轮所有工具调用，输出耗时统计
                    if (toolUseBlocks != null) {
                        for (ToolUseBlock block : toolUseBlocks) {
                            Long startMs = toolCallStartTimes.remove(block.getId());
                            if (startMs != null) {
                                long elapsed = System.currentTimeMillis() - startMs;
                                log.info("[TokenMonitor] Tool call finished: name={}, elapsed={}ms",
                                        block.getName(), elapsed);
                            }
                        }
                    }
                })
                .doOnNext(event -> {
                    if (event instanceof ToolResultEndEvent end) {
                        log.info("[TokenMonitor] Tool result: name={}, state={}, toolCallId={}",
                                end.getToolCallName(), end.getState(), end.getToolCallId());
                    }
                });
    }

    /** 获取当前会话 Token 统计快照 */
    public TokenStats getStats() {
        return new TokenStats(
                reasoningCount.get(),
                totalPromptTokens.get(),
                totalCompletionTokens.get(),
                totalToolCalls.get());
    }

    /** Token 统计快照 */
    public record TokenStats(
            int reasoningCount,
            long promptTokens,
            long completionTokens,
            int toolCalls) {

        public long totalTokens() {
            return promptTokens + completionTokens;
        }
    }
}
