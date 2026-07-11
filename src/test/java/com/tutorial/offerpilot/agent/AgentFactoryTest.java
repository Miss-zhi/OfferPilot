/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent;

import com.tutorial.offerpilot.agent.middleware.CostControlMiddleware;
import com.tutorial.offerpilot.agent.middleware.TokenMonitorMiddleware;
import com.tutorial.offerpilot.agent.tool.*;
import com.tutorial.offerpilot.config.AgentScopeProperties;
import com.tutorial.offerpilot.service.ApiKeyEncryption;
import com.tutorial.offerpilot.service.ModelConfigService;
import com.tutorial.offerpilot.service.UserMemoryService;
import com.tutorial.offerpilot.service.UserModelService;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentFactory 单元测试")
class AgentFactoryTest {

    @Mock
    private AgentScopeProperties properties;

    @Mock
    private AgentScopeProperties.ModelConfig modelConfig;

    @Mock
    private UserMemoryService userMemoryService;

    @Mock
    private UserModelService userModelService;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private ApiKeyEncryption apiKeyEncryption;

    @Mock
    private AnswerAnalyzeTool answerAnalyzeTool;
    @Mock
    private AnswerSearchTool answerSearchTool;
    @Mock
    private AudioTranscribeTool audioTranscribeTool;
    @Mock
    private CompanySearchTool companySearchTool;
    @Mock
    private MockInterviewTool mockInterviewTool;
    @Mock
    private ProgressTrackTool progressTrackTool;
    @Mock
    private QuestionSearchTool questionSearchTool;
    @Mock
    private ResourceSearchTool resourceSearchTool;
    @Mock
    private ResumeEvaluateTool resumeEvaluateTool;
    @Mock
    private ResumeParseTool resumeParseTool;
    @Mock
    private SalaryTool salaryTool;
    @Mock
    private SmartSearchTool smartSearchTool;

    @InjectMocks
    private AgentFactory agentFactory;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getModel()).thenReturn(modelConfig);
        lenient().when(modelConfig.getProvider()).thenReturn("dashscope");
        lenient().when(modelConfig.getModelName()).thenReturn("qwen-max");
        // Default: no user/global model config → fallback to yml config
        lenient().when(userModelService.getUserModelConfig(anyString())).thenReturn(null);
        lenient().when(modelConfigService.getGlobalDefault()).thenReturn(null);
    }

    /** 便捷方法：设置 HarnessAgent.builder() 和 ModelRegistry.resolve() 静态 Mock。
     *  每次 build() 返回新的 HarnessAgent mock（避免缓存测试误判）。
     *  使用 lenient 避免部分测试未触发 build 时报 UnnecessaryStubbing。 */
    private static TestMocks setupTestMocks() {
        MockedStatic<HarnessAgent> harnessStatic = mockStatic(HarnessAgent.class);
        MockedStatic<ModelRegistry> registryStatic = mockStatic(ModelRegistry.class);

        HarnessAgent.Builder builder = mock(HarnessAgent.Builder.class, RETURNS_SELF);
        Model mockModel = mock(Model.class);

        lenient().when(HarnessAgent.builder()).thenReturn(builder);
        lenient().when(builder.build()).thenAnswer(inv -> {
            HarnessAgent a = mock(HarnessAgent.class);
            when(a.getName()).thenReturn("offerpilot_default");
            return a;
        });

        registryStatic.when(() -> ModelRegistry.resolve(anyString())).thenReturn(mockModel);
        registryStatic.when(() -> ModelRegistry.resolve(anyString(), any())).thenReturn(mockModel);

        return new TestMocks(harnessStatic, registryStatic, builder, mockModel);
    }

    private record TestMocks(MockedStatic<HarnessAgent> harness, MockedStatic<ModelRegistry> registry,
                             HarnessAgent.Builder builder, Model model) implements AutoCloseable {
        @Override public void close() { harness.close(); registry.close(); }
    }

    // ==================== Caffeine 缓存池 ====================

    @Nested
    @DisplayName("Caffeine 缓存池")
    class CaffeineCacheTests {

        @Test
        @DisplayName("相同 userId → 返回缓存实例（只调用一次 build）")
        void sameUserId_returnsCachedAgent() {
            try (TestMocks mocks = setupTestMocks()) {

                HarnessAgent a1 = agentFactory.getOrCreateAgent("user1");
                HarnessAgent a2 = agentFactory.getOrCreateAgent("user1");

                assertSame(a1, a2, "相同 userId 应返回同一实例");
                verify(mocks.builder(), times(1)).build();
            }
        }

        @Test
        @DisplayName("不同 userId → 返回不同实例（各自创建一次）")
        void differentUserIds_returnsDifferentAgents() {
            try (TestMocks mocks = setupTestMocks()) {

                HarnessAgent a1 = agentFactory.getOrCreateAgent("user1");
                HarnessAgent a2 = agentFactory.getOrCreateAgent("user2");

                assertNotSame(a1, a2, "不同 userId 应返回不同实例");
                verify(mocks.builder(), times(2)).build();
            }
        }

        @Test
        @DisplayName("evictAgent → 缓存失效，下次 getOrCreate 重建")
        void evictAgent_invalidatesCache() {
            try (TestMocks mocks = setupTestMocks()) {

                HarnessAgent a1 = agentFactory.getOrCreateAgent("user1");
                agentFactory.evictAgent("user1");
                HarnessAgent a2 = agentFactory.getOrCreateAgent("user1");

                assertNotSame(a1, a2, "evict 后应重建 Agent");
                verify(mocks.builder(), times(2)).build();
            }
        }

        @Test
        @DisplayName("evictAgent 不存在的 key → 无异常")
        void evictAgent_nonExistentKey_shouldNotThrow() {
            assertDoesNotThrow(() -> agentFactory.evictAgent("nonexistent"));
        }

        @Test
        @DisplayName("多用户并发访问 → 各自缓存不互相干扰")
        void concurrentAccess_differentUsers_isolated() throws Exception {
            try (TestMocks mocks = setupTestMocks()) {

                int threadCount = 10;
                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                CountDownLatch latch = new CountDownLatch(threadCount);
                AtomicInteger errors = new AtomicInteger(0);

                for (int i = 0; i < threadCount; i++) {
                    final String userId = "user" + i;
                    executor.submit(() -> {
                        try {
                            HarnessAgent a1 = agentFactory.getOrCreateAgent(userId);
                            HarnessAgent a2 = agentFactory.getOrCreateAgent(userId);
                            if (a1 != a2) {
                                errors.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(5, TimeUnit.SECONDS);
                executor.shutdown();

                assertEquals(0, errors.get(), "同一 userId 并发访问不应出现不同实例");
            }
        }
    }

    // ==================== Agent 构建逻辑 ====================

    @Nested
    @DisplayName("Agent 构建逻辑")
    class AgentBuildingTests {

        @Test
        @DisplayName("Agent name = 'offerpilot_' + userId")
        void agentName_shouldBeOfferpilotPlusUserId() {
            try (TestMocks mocks = setupTestMocks()) {

                agentFactory.getOrCreateAgent("zhangsan");

                verify(mocks.builder()).name("offerpilot_zhangsan");
            }
        }

        @Test
        @DisplayName("model identifier = 'provider:modelName'")
        void modelIdentifier_shouldBeProviderPlusModelName() {
            try (TestMocks mocks = setupTestMocks()) {

                when(modelConfig.getProvider()).thenReturn("openai");
                when(modelConfig.getModelName()).thenReturn("gpt-4");

                agentFactory.getOrCreateAgent("user1");

                // Builder receives Model instance (resolved by ModelRegistry)
                verify(mocks.builder()).model(any(Model.class));
                // ModelRegistry.resolve was called with the correct fallback modelId + context
                mocks.registry().verify(() -> ModelRegistry.resolve(eq("openai:gpt-4"), any()));
            }
        }

        @Test
        @DisplayName("系统提示词非空且包含关键角色描述")
        void sysPrompt_shouldContainOfferPilotRole() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<String> sysPromptCaptor = ArgumentCaptor.forClass(String.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).sysPrompt(sysPromptCaptor.capture());
                String prompt = sysPromptCaptor.getValue();
                assertNotNull(prompt);
                assertFalse(prompt.isBlank());
                assertTrue(prompt.contains("OfferPilot"), "提示词应包含 OfferPilot 角色名");
                assertTrue(prompt.contains("调度中心"), "提示词应包含调度中心角色描述");
                assertTrue(prompt.contains("子 Agent"), "提示词应包含子 Agent 分派指南");
            }
        }

        @Test
        @DisplayName("enablePlanMode() 被调用")
        void enablePlanMode_shouldBeCalled() {
            try (TestMocks mocks = setupTestMocks()) {

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).enablePlanMode();
            }
        }

        @Test
        @DisplayName("enableTaskList() 被调用")
        void enableTaskList_shouldBeCalled() {
            try (TestMocks mocks = setupTestMocks()) {

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).enableTaskList();
            }
        }

        @Test
        @DisplayName("middleware 传入 2 个（TokenMonitor + CostControl）")
        void middleware_shouldAddTwoInstances() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<MiddlewareBase> middlewareCaptor = ArgumentCaptor.forClass(MiddlewareBase.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder(), times(2)).middleware(middlewareCaptor.capture());
                var captured = middlewareCaptor.getAllValues();

                boolean hasTokenMonitor = captured.stream()
                        .anyMatch(m -> m instanceof TokenMonitorMiddleware);
                boolean hasCostControl = captured.stream()
                        .anyMatch(m -> m instanceof CostControlMiddleware);

                assertTrue(hasTokenMonitor, "应包含 TokenMonitorMiddleware");
                assertTrue(hasCostControl, "应包含 CostControlMiddleware");
            }
        }

        @Test
        @DisplayName("每次构建 → 中间件是新实例（线程安全）")
        void middleware_shouldBeNewInstancesEachBuild() {
            try (TestMocks mocks = setupTestMocks()) {

                // 构建 user1
                agentFactory.getOrCreateAgent("user1");
                // 逐出并重建 user1
                agentFactory.evictAgent("user1");
                agentFactory.getOrCreateAgent("user1");

                // 两次构建共调用 middleware 4 次 (TokenMonitor + CostControl × 2)
                ArgumentCaptor<MiddlewareBase> captor = ArgumentCaptor.forClass(MiddlewareBase.class);
                verify(mocks.builder(), times(4)).middleware(captor.capture());

                var allMw = captor.getAllValues();
                // allMw[0]=TokenMonitor1, allMw[1]=CostControl1, allMw[2]=TokenMonitor2, allMw[3]=CostControl2
                assertNotSame(allMw.get(0), allMw.get(2),
                        "每次构建的 TokenMonitorMiddleware 应为新实例");
                assertNotSame(allMw.get(1), allMw.get(3),
                        "每次构建的 CostControlMiddleware 应为新实例");
            }
        }
    }

    // ==================== Toolkit 构建 ====================

    @Nested
    @DisplayName("Toolkit 构建")
    class ToolkitBuildingTests {

        @Test
        @DisplayName("Toolkit 包含 4 个分组 + 至少 12 个工具（11 业务 + meta）")
        void toolkit_shouldHaveCorrectGroupsAndTools() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<Toolkit> toolkitCaptor = ArgumentCaptor.forClass(Toolkit.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).toolkit(toolkitCaptor.capture());
                Toolkit toolkit = toolkitCaptor.getValue();

                assertNotNull(toolkit);
                // 4 groups: knowledge_retrieval, resume_analysis, interview, utility
                assertNotNull(toolkit.getActiveGroups());
                assertEquals(4, toolkit.getActiveGroups().size(),
                        "应有 4 个工具分组");

                // 12 tools: 11 business + meta
                assertNotNull(toolkit.getToolNames());
                assertTrue(toolkit.getToolNames().size() >= 12,
                        "应有至少 12 个工具，实际: " + toolkit.getToolNames().size());
            }
        }

        @Test
        @DisplayName("knowledge_retrieval 分组含 4 个检索工具")
        void knowledgeRetrievalGroup_hasCorrectTools() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<Toolkit> toolkitCaptor = ArgumentCaptor.forClass(Toolkit.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).toolkit(toolkitCaptor.capture());
                Toolkit toolkit = toolkitCaptor.getValue();

                assertTrue(toolkit.getToolNames().contains("search_answers"),
                        "应包含 search_answers");
                assertTrue(toolkit.getToolNames().contains("search_questions"),
                        "应包含 search_questions");
                assertTrue(toolkit.getToolNames().contains("search_company_interviews"),
                        "应包含 search_company_interviews");
                assertTrue(toolkit.getToolNames().contains("search_resources"),
                        "应包含 search_resources");
            }
        }

        @Test
        @DisplayName("resume_analysis 分组含 2 个简历工具")
        void resumeAnalysisGroup_hasCorrectTools() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<Toolkit> toolkitCaptor = ArgumentCaptor.forClass(Toolkit.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).toolkit(toolkitCaptor.capture());
                Toolkit toolkit = toolkitCaptor.getValue();

                assertTrue(toolkit.getToolNames().contains("parse_resume"),
                        "应包含 parse_resume");
                assertTrue(toolkit.getToolNames().contains("evaluate_resume"),
                        "应包含 evaluate_resume");
            }
        }

        @Test
        @DisplayName("interview 分组含 3 个面试工具")
        void interviewGroup_hasCorrectTools() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<Toolkit> toolkitCaptor = ArgumentCaptor.forClass(Toolkit.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).toolkit(toolkitCaptor.capture());
                Toolkit toolkit = toolkitCaptor.getValue();

                assertTrue(toolkit.getToolNames().contains("generate_next_question"),
                        "应包含 generate_next_question");
                assertTrue(toolkit.getToolNames().contains("analyze_answer"),
                        "应包含 analyze_answer");
                assertTrue(toolkit.getToolNames().contains("transcribe_audio"),
                        "应包含 transcribe_audio");
            }
        }

        @Test
        @DisplayName("utility 分组含 2 个通用工具")
        void utilityGroup_hasCorrectTools() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<Toolkit> toolkitCaptor = ArgumentCaptor.forClass(Toolkit.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).toolkit(toolkitCaptor.capture());
                Toolkit toolkit = toolkitCaptor.getValue();

                assertTrue(toolkit.getToolNames().contains("track_progress"),
                        "应包含 track_progress");
                assertTrue(toolkit.getToolNames().contains("search_salary"),
                        "应包含 search_salary");
            }
        }

        @Test
        @DisplayName("registerMetaTool → Meta 工具被注册")
        void metaTool_shouldBeRegistered() {
            try (TestMocks mocks = setupTestMocks()) {

                ArgumentCaptor<Toolkit> toolkitCaptor = ArgumentCaptor.forClass(Toolkit.class);

                agentFactory.getOrCreateAgent("user1");

                verify(mocks.builder()).toolkit(toolkitCaptor.capture());
                Toolkit toolkit = toolkitCaptor.getValue();

                // Meta tool name varies by AgentScope version; verify size exceeds business count
                assertTrue(toolkit.getToolNames().size() >= 12,
                        "registerMetaTool 应注册 meta 工具使总数 >= 12");
            }
        }
    }

    // ==================== 异常场景 ====================

    @Nested
    @DisplayName("异常场景")
    class EdgeCaseTests {

        @Test
        @DisplayName("userId 为 null → Caffeine 拒绝 null key 抛 NPE")
        void nullUserId_shouldThrowNpe() {
            try (TestMocks mocks = setupTestMocks()) {

                assertThrows(NullPointerException.class,
                        () -> agentFactory.getOrCreateAgent(null),
                        "Caffeine Cache 不接受 null key，应抛 NPE");
            }
        }

        @Test
        @DisplayName("相同 userId 反复 evict + get → 每次都重建")
        void repeatedEvict_shouldRebuildEachTime() {
            try (TestMocks mocks = setupTestMocks()) {

                for (int i = 0; i < 5; i++) {
                    agentFactory.getOrCreateAgent("user1");
                    agentFactory.evictAgent("user1");
                }

                verify(mocks.builder(), times(5)).build();
            }
        }
    }
}
