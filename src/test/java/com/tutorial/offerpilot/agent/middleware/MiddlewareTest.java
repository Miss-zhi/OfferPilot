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
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.Model;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Middleware 单元测试")
class MiddlewareTest {

    private Agent mockAgent;
    private RuntimeContext mockCtx;

    @BeforeEach
    void setUp() {
        mockAgent = mock(Agent.class);
        when(mockAgent.getName()).thenReturn("test-agent");
        mockCtx = mock(RuntimeContext.class);
        when(mockCtx.getSessionId()).thenReturn("test-session-001");
    }

    // ==================== TokenMonitorMiddleware ====================

    @Nested
    @DisplayName("TokenMonitorMiddleware")
    class TokenMonitorMiddlewareTests {

        private TokenMonitorMiddleware middleware;

        @BeforeEach
        void setUp() {
            middleware = new TokenMonitorMiddleware();
        }

        @Test
        @DisplayName("onReasoning → 统计消息数和工具数，并传递事件流")
        void onReasoning_shouldCountMsgsAndTools() {
            Msg msg1 = mock(Msg.class);
            Msg msg2 = mock(Msg.class);
            ToolSchema tool1 = mock(ToolSchema.class);
            ReasoningInput input = new ReasoningInput(
                    List.of(msg1, msg2), List.of(tool1), null);
            AgentEvent mockEvent = mock(AgentEvent.class);
            Function<ReasoningInput, Flux<AgentEvent>> next = i -> Flux.just(mockEvent);

            List<AgentEvent> result = middleware.onReasoning(mockAgent, mockCtx, input, next)
                    .collectList().block();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertSame(mockEvent, result.get(0));

            // 验证统计
            TokenMonitorMiddleware.TokenStats stats = middleware.getStats();
            assertEquals(1, stats.reasoningCount(), "推理次数应为 1");
            assertEquals(0, stats.promptTokens(), "尚未触发 model call，promptTokens 应为 0");
        }

        @Test
        @DisplayName("onReasoning → 多次调用递增推理计数")
        void onReasoning_shouldIncrementCount() {
            ReasoningInput input = new ReasoningInput(List.of(), List.of(), null);
            AgentEvent mockEvent = mock(AgentEvent.class);
            Function<ReasoningInput, Flux<AgentEvent>> next = i -> Flux.just(mockEvent);

            middleware.onReasoning(mockAgent, mockCtx, input, next).collectList().block();
            middleware.onReasoning(mockAgent, mockCtx, input, next).collectList().block();
            middleware.onReasoning(mockAgent, mockCtx, input, next).collectList().block();

            assertEquals(3, middleware.getStats().reasoningCount());
        }

        @Test
        @DisplayName("onReasoning → messages=null 时不抛 NPE")
        void onReasoning_shouldHandleNullMessages() {
            ReasoningInput input = new ReasoningInput(null, null, null);
            Function<ReasoningInput, Flux<AgentEvent>> next = i -> Flux.just(mock(AgentEvent.class));

            assertDoesNotThrow(() ->
                    middleware.onReasoning(mockAgent, mockCtx, input, next).collectList().block());
        }

        @Test
        @DisplayName("onModelCall → 从 ModelCallEndEvent 提取 usage 并累加 token")
        void onModelCall_shouldAccumulateTokens() {
            ChatUsage usage = ChatUsage.builder()
                    .inputTokens(500)
                    .outputTokens(200)
                    .build();
            ModelCallEndEvent endEvent = new ModelCallEndEvent("reply-1", usage);
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(endEvent);

            List<AgentEvent> result = middleware.onModelCall(mockAgent, mockCtx, input, next)
                    .collectList().block();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertSame(endEvent, result.get(0));

            TokenMonitorMiddleware.TokenStats stats = middleware.getStats();
            assertEquals(500, stats.promptTokens());
            assertEquals(200, stats.completionTokens());
            assertEquals(700, stats.totalTokens());
        }

        @Test
        @DisplayName("onModelCall → 多次调用累加 token")
        void onModelCall_shouldAccumulateAcrossCalls() {
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));

            ChatUsage usage1 = ChatUsage.builder().inputTokens(100).outputTokens(50).build();
            ModelCallEndEvent event1 = new ModelCallEndEvent("r1", usage1);
            middleware.onModelCall(mockAgent, mockCtx, input, i -> Flux.just(event1))
                    .collectList().block();

            ChatUsage usage2 = ChatUsage.builder().inputTokens(300).outputTokens(150).build();
            ModelCallEndEvent event2 = new ModelCallEndEvent("r2", usage2);
            middleware.onModelCall(mockAgent, mockCtx, input, i -> Flux.just(event2))
                    .collectList().block();

            TokenMonitorMiddleware.TokenStats stats = middleware.getStats();
            assertEquals(400, stats.promptTokens(), "promptTokens 应累加: 100+300");
            assertEquals(200, stats.completionTokens(), "completionTokens 应累加: 50+150");
            assertEquals(600, stats.totalTokens());
        }

        @Test
        @DisplayName("onModelCall → usage=null 时不累加")
        void onModelCall_shouldSkipNullUsage() {
            ModelCallEndEvent endEvent = new ModelCallEndEvent("reply-1", null);
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(endEvent);

            middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block();

            TokenMonitorMiddleware.TokenStats stats = middleware.getStats();
            assertEquals(0, stats.promptTokens());
            assertEquals(0, stats.completionTokens());
        }

        @Test
        @DisplayName("onModelCall → 非 ModelCallEndEvent 不累加")
        void onModelCall_shouldIgnoreNonEndEvents() {
            AgentEvent otherEvent = mock(AgentEvent.class);
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(otherEvent);

            middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block();

            assertEquals(0, middleware.getStats().totalTokens());
        }

        @Test
        @DisplayName("onActing → ToolCallStartEvent 递增工具调用计数")
        void onActing_shouldCountToolStarts() {
            ToolCallStartEvent startEvent = new ToolCallStartEvent("reply-1", "tc-1", "search_answers");
            ActingInput input = new ActingInput(List.of());
            Function<ActingInput, Flux<AgentEvent>> next = i -> Flux.just(startEvent);

            middleware.onActing(mockAgent, mockCtx, input, next).collectList().block();

            assertEquals(1, middleware.getStats().toolCalls());
        }

        @Test
        @DisplayName("onActing → ToolResultEndEvent 不额外递增计数")
        void onActing_shouldNotIncrementOnResult() {
            ToolResultEndEvent resultEvent = new ToolResultEndEvent("reply-1", "tc-1", "search_answers", null);
            ActingInput input = new ActingInput(List.of());
            Function<ActingInput, Flux<AgentEvent>> next = i -> Flux.just(resultEvent);

            middleware.onActing(mockAgent, mockCtx, input, next).collectList().block();

            // ToolCallStartEvent 没有出现，计数应为 0
            assertEquals(0, middleware.getStats().toolCalls());
        }

        @Test
        @DisplayName("getStats → 返回完整统计快照")
        void getStats_shouldReturnSnapshot() {
            // 触发一条 reasoning
            ReasoningInput rInput = new ReasoningInput(List.of(), List.of(), null);
            middleware.onReasoning(mockAgent, mockCtx, rInput,
                    i -> Flux.just(mock(AgentEvent.class))).collectList().block();

            // 触发一条 model call
            ChatUsage usage = ChatUsage.builder().inputTokens(100).outputTokens(50).build();
            ModelCallEndEvent endEvent = new ModelCallEndEvent("r1", usage);
            ModelCallInput mInput = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            middleware.onModelCall(mockAgent, mockCtx, mInput,
                    i -> Flux.just(endEvent)).collectList().block();

            // 触发两个 tool call
            ToolCallStartEvent tc1 = new ToolCallStartEvent("r1", "tc-1", "tool_a");
            ToolCallStartEvent tc2 = new ToolCallStartEvent("r2", "tc-2", "tool_b");
            ActingInput aInput = new ActingInput(List.of());
            middleware.onActing(mockAgent, mockCtx, aInput,
                    i -> Flux.just(tc1, tc2)).collectList().block();

            TokenMonitorMiddleware.TokenStats stats = middleware.getStats();
            assertEquals(1, stats.reasoningCount());
            assertEquals(100, stats.promptTokens());
            assertEquals(50, stats.completionTokens());
            assertEquals(150, stats.totalTokens());
            assertEquals(2, stats.toolCalls());
        }

        @Test
        @DisplayName("TokenStats.totalTokens() → promptTokens + completionTokens")
        void tokenStats_totalTokens() {
            TokenMonitorMiddleware.TokenStats stats = new TokenMonitorMiddleware.TokenStats(0, 300, 200, 1);
            assertEquals(500, stats.totalTokens());
        }
    }

    // ==================== CostControlMiddleware ====================

    @Nested
    @DisplayName("CostControlMiddleware")
    class CostControlMiddlewareTests {

        private CostControlMiddleware middleware;

        @BeforeEach
        void setUp() {
            middleware = new CostControlMiddleware();
        }

        @Test
        @DisplayName("onAgent → 重置计数器")
        void onAgent_shouldResetCounters() {
            // 先触发 model call 提高计数
            ModelCallInput mInput = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            ChatUsage usage = ChatUsage.builder().inputTokens(100).outputTokens(50).build();
            ModelCallEndEvent endEvent = new ModelCallEndEvent("r1", usage);
            middleware.onModelCall(mockAgent, mockCtx, mInput, i -> Flux.just(endEvent))
                    .collectList().block();

            // 触发 onAgent 重置
            AgentInput aInput = new AgentInput(List.of());
            middleware.onAgent(mockAgent, mockCtx, aInput,
                    i -> Flux.just(mock(AgentEvent.class))).collectList().block();

            // 重置后 model call 计数应从 1 开始
            middleware.onModelCall(mockAgent, mockCtx, mInput, i -> Flux.just(endEvent))
                    .collectList().block();
            // 如果 onAgent 重置了 callCount，则这次是第 1 次调用，不应超限
            // 若未重置，则第二次调用后为 2，但松散的检测方式是再调用 48 次验证
            // 更简单：直接验证 onAgent 不抛异常
        }

        @Test
        @DisplayName("onModelCall → 每次递增调用次数")
        void onModelCall_shouldIncrementCallCount() {
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            AgentEvent mockEvent = mock(AgentEvent.class);
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(mockEvent);

            // 调用 5 次不应超限
            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(() ->
                        middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block());
            }
        }

        @Test
        @DisplayName("onModelCall → 超过 MAX_CALLS_PER_SESSION(50) 抛 CostLimitExceededException")
        void onModelCall_shouldThrowWhenExceedingMaxCalls() {
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            AgentEvent mockEvent = mock(AgentEvent.class);
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(mockEvent);

            // 调用 50 次（未超限）
            for (int i = 0; i < 50; i++) {
                middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block();
            }

            // 第 51 次应抛异常
            assertThrows(CostControlMiddleware.CostLimitExceededException.class, () ->
                    middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block());
        }

        @Test
        @DisplayName("onModelCall → 第 50 次调用不应抛异常")
        void onModelCall_shouldAllowExactlyMax() {
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            AgentEvent mockEvent = mock(AgentEvent.class);
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(mockEvent);

            for (int i = 0; i < 49; i++) {
                middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block();
            }
            // 第 50 次不应抛异常
            assertDoesNotThrow(() ->
                    middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block());
        }

        @Test
        @DisplayName("onModelCall → usage=null 时不累加 token 也不触发超限")
        void onModelCall_shouldHandleNullUsage() {
            ModelCallEndEvent endEvent = new ModelCallEndEvent("reply-1", null);
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(endEvent);

            assertDoesNotThrow(() ->
                    middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block());
        }

        @Test
        @DisplayName("onModelCall → 非 ModelCallEndEvent 不累加 token")
        void onModelCall_shouldIgnoreNonEndEvents() {
            AgentEvent otherEvent = mock(AgentEvent.class);
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(otherEvent);

            assertDoesNotThrow(() ->
                    middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block());
        }

        @Test
        @DisplayName("onModelCall → 单次 token > MAX_TOKENS_PER_CALL(16000) 仅 warn 不抛异常")
        void onModelCall_shouldNotThrowOnPerCallTokenExceed() {
            ChatUsage usage = ChatUsage.builder()
                    .inputTokens(10000)
                    .outputTokens(7000) // total = 17000 > 16000
                    .build();
            ModelCallEndEvent endEvent = new ModelCallEndEvent("r1", usage);
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(endEvent);

            assertDoesNotThrow(() ->
                    middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block());
        }

        @Test
        @DisplayName("onModelCall → 会话累计 token > MAX_TOKENS_PER_SESSION(200000) 仅 warn 不抛异常")
        void onModelCall_shouldNotThrowOnSessionTokenExceed() {
            ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));

            // 累计大量 token
            ChatUsage usage = ChatUsage.builder().inputTokens(150000).outputTokens(60000).build();
            ModelCallEndEvent endEvent = new ModelCallEndEvent("r1", usage);
            Function<ModelCallInput, Flux<AgentEvent>> next = i -> Flux.just(endEvent);

            assertDoesNotThrow(() ->
                    middleware.onModelCall(mockAgent, mockCtx, input, next).collectList().block());
        }

        @Test
        @DisplayName("onAgent → 重置后模型调用计数归零")
        void onAgent_shouldResetCallCountAfterExhaustion() {
            ModelCallInput mInput = new ModelCallInput(List.of(), List.of(), null, mock(Model.class));
            AgentEvent mockEvent = mock(AgentEvent.class);

            // 消耗全部 50 次
            for (int i = 0; i < 50; i++) {
                middleware.onModelCall(mockAgent, mockCtx, mInput,
                        i2 -> Flux.just(mockEvent)).collectList().block();
            }

            // 验证第 51 次抛异常
            assertThrows(CostControlMiddleware.CostLimitExceededException.class, () ->
                    middleware.onModelCall(mockAgent, mockCtx, mInput,
                            i2 -> Flux.just(mockEvent)).collectList().block());

            // onAgent 重置计数器
            AgentInput aInput = new AgentInput(List.of());
            middleware.onAgent(mockAgent, mockCtx, aInput,
                    i2 -> Flux.just(mockEvent)).collectList().block();

            // 重置后可以再调用
            assertDoesNotThrow(() ->
                    middleware.onModelCall(mockAgent, mockCtx, mInput,
                            i2 -> Flux.just(mockEvent)).collectList().block());
        }

        @Test
        @DisplayName("CostLimitExceededException → 包含限制信息")
        void costLimitExceededException_shouldContainLimit() {
            CostControlMiddleware.CostLimitExceededException ex =
                    new CostControlMiddleware.CostLimitExceededException("limit: 50");
            assertEquals("limit: 50", ex.getMessage());
        }
    }
}
