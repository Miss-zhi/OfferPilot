/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.tutorial.offerpilot.agent.middleware.CostControlMiddleware;
import com.tutorial.offerpilot.agent.middleware.TokenMonitorMiddleware;
import com.tutorial.offerpilot.agent.tool.*;
import com.tutorial.offerpilot.config.AgentScopeProperties;
import com.tutorial.offerpilot.entity.ModelConfig;
import com.tutorial.offerpilot.service.ApiKeyEncryption;
import com.tutorial.offerpilot.service.ModelConfigService;
import com.tutorial.offerpilot.service.UserMemoryService;
import com.tutorial.offerpilot.service.UserModelService;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * Agent 构建工厂 + Caffeine 有界缓存池管理。
 * 集成 AgentScope Java v2 HarnessAgent，支持 planMode + taskList + 工具分组。
 */
@Slf4j
@Component
public class AgentFactory {

    private static final int MAX_AGENTS = 500;
    private static final int EVICT_MINUTES = 30;

    /**
     * OpenAI 兼容但非 openai 前缀的 Provider。
     * AgentScope 框架中这些 Provider 无独立 SPI ModelProvider，
     * 必须映射为 openai: 前缀 + 自定义 baseUrl 以复用 OpenAIModelProvider。
     */
    private static final Set<String> OPENAI_COMPATIBLE_PROVIDERS =
            Set.of("deepseek", "siliconflow", "volcengine");

    private final AgentScopeProperties properties;
    private final UserMemoryService userMemoryService;
    private final UserModelService userModelService;
    private final ModelConfigService modelConfigService;
    private final ApiKeyEncryption apiKeyEncryption;

    /** 工具实例 — 10 个本地工具类 + MCP web-search */
    private final AnswerAnalyzeTool answerAnalyzeTool;
    private final AnswerSearchTool answerSearchTool;
    private final AudioTranscribeTool audioTranscribeTool;
    private final ListKnowledgeBasesTool listKnowledgeBasesTool;
    private final MockInterviewTool mockInterviewTool;
    private final QuestionSearchTool questionSearchTool;
    private final ResourceSearchTool resourceSearchTool;
    private final ResumeEvaluateTool resumeEvaluateTool;
    private final ResumeParseTool resumeParseTool;
    private final SmartSearchTool smartSearchTool;

    /** Caffeine 有界缓存：最多 MAX_AGENTS 个 HarnessAgent，EVICT_MINUTES 分钟未访问自动淘汰 */
    private final Cache<String, HarnessAgent> agentPool = Caffeine.newBuilder()
            .maximumSize(MAX_AGENTS)
            .expireAfterAccess(Duration.ofMinutes(EVICT_MINUTES))
            .removalListener((key, agent, cause) ->
                    log.info("Evicted agent: userId={}, cause={}", key, cause))
            .build();

    public AgentFactory(AgentScopeProperties properties,
                        UserMemoryService userMemoryService,
                        UserModelService userModelService,
                        ModelConfigService modelConfigService,
                        ApiKeyEncryption apiKeyEncryption,
                        AnswerAnalyzeTool answerAnalyzeTool,
                        AnswerSearchTool answerSearchTool,
                        AudioTranscribeTool audioTranscribeTool,
                        MockInterviewTool mockInterviewTool,
                        QuestionSearchTool questionSearchTool,
                        ResourceSearchTool resourceSearchTool,
                        ResumeEvaluateTool resumeEvaluateTool,
                        ResumeParseTool resumeParseTool,
                        ListKnowledgeBasesTool listKnowledgeBasesTool,
                        SmartSearchTool smartSearchTool) {
        this.properties = properties;
        this.userMemoryService = userMemoryService;
        this.userModelService = userModelService;
        this.modelConfigService = modelConfigService;
        this.apiKeyEncryption = apiKeyEncryption;
        this.answerAnalyzeTool = answerAnalyzeTool;
        this.answerSearchTool = answerSearchTool;
        this.audioTranscribeTool = audioTranscribeTool;
        this.listKnowledgeBasesTool = listKnowledgeBasesTool;
        this.mockInterviewTool = mockInterviewTool;
        this.questionSearchTool = questionSearchTool;
        this.resourceSearchTool = resourceSearchTool;
        this.resumeEvaluateTool = resumeEvaluateTool;
        this.resumeParseTool = resumeParseTool;
        this.smartSearchTool = smartSearchTool;
    }

    /**
     * 获取或创建 HarnessAgent 实例。
     */
    public HarnessAgent getOrCreateAgent(String userId) {
        return agentPool.get(userId, this::buildAgent);
    }

    private HarnessAgent buildAgent(String userId) {
        log.info("Building HarnessAgent for userId={}", userId);

        // 1. 构建 Toolkit — 3 个分组 + 注册所有工具
        Toolkit toolkit = buildToolkit();

        // 2. 按优先级选择模型: 用户私有 > 用户默认 > 全局默认 > application.yml 兜底
        Model model = resolveModel(userId);

        // 3. 系统提示词（全能助手）
        String sysPrompt = buildSystemPrompt();

        // 4. 中间件（每次构建新实例以确保线程安全统计）
        TokenMonitorMiddleware tokenMonitor = new TokenMonitorMiddleware();
        CostControlMiddleware costControl = new CostControlMiddleware();

        // 5. Permission
        PermissionContextState perms = buildPermissions();

        // 6. 构建 HarnessAgent（单 Agent，不再派发子 Agent）
        HarnessAgent agent = HarnessAgent.builder()
                .name("offerpilot_" + userId)
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(toolkit)
                .workspace(Path.of("./workspace"))
                .permissionContext(perms)
                .middleware(tokenMonitor)
                .middleware(costControl)
                .enablePlanMode()
                .enableTaskList()
                .build();

        log.info("HarnessAgent built: name={}, model={}", agent.getName(), model);
        return agent;
    }

    /**
     * 构建 Toolkit，注册 10 个本地工具 + MCP web-search（不分组，避免 AgentScope 分组激活问题）。
     */
    private Toolkit buildToolkit() {
        Toolkit toolkit = new Toolkit();

        // ---- 注册知识库元信息工具（无分组） ----
        toolkit.registration()
                .tool(listKnowledgeBasesTool)
                .apply();

        // ---- 注册知识检索工具（无分组） ----
        toolkit.registration()
                .tool(answerSearchTool)
                .apply();
        toolkit.registration()
                .tool(questionSearchTool)
                .apply();
        toolkit.registration()
                .tool(resourceSearchTool)
                .apply();
        toolkit.registration()
                .tool(smartSearchTool)
                .apply();

        // ---- 注册简历分析工具（无分组） ----
        toolkit.registration()
                .tool(resumeParseTool)
                .apply();
        toolkit.registration()
                .tool(resumeEvaluateTool)
                .apply();

        // ---- 注册面试工具（无分组） ----
        toolkit.registration()
                .tool(mockInterviewTool)
                .apply();
        toolkit.registration()
                .tool(answerAnalyzeTool)
                .apply();
        toolkit.registration()
                .tool(audioTranscribeTool)
                .apply();

        // ---- 注册 MCP web-search 工具 ----
        registerMcpWebSearch(toolkit);

        log.info("Toolkit built: tools={}", toolkit.getToolNames().size());

        return toolkit;
    }

    /**
     * 构建系统提示词，定义 OfferPilot 全能助手的角色和行为。
     */
    private String buildSystemPrompt() {
        return """
                You are OfferPilot 面试诊断助手。
    
                【你的能力】
                1. 简历智能诊断 — 解析 PDF 简历，多维度评估，给出优化建议
                2. AI 模拟面试 — 扮演面试官，支持 TECH_DEEP/BEHAVIOR/SYSTEM_DESIGN/PRESSURE 模式
                3. 录音/文字分析 — 转写录音，六维深度分析面试表现
                4. 知识检索与智能问答 — 检索面试题库、优秀答案、公司面经、学习资源，不足时联网兜底
    
                【可用工具及使用场景】
                | 工具 | 使用场景 |
                | parse_resume | 用户上传 PDF 简历时解析 |
                | evaluate_resume | 简历解析后，评估质量并给出建议 |
                | generate_next_question | 模拟面试中，获取出题指导 |
                | analyze_answer | 用户回答后，分析回答质量；面试复盘逐题分析 |
                | transcribe_audio | 用户上传录音文件时转写 |
                | search_questions | 检索面试题库 |
                | search_answers | 检索优秀答案对标 |
                | smart_search | 通用知识库语义检索（自动识别意图并路由到对应子库） |
                | search_resources | 检索学习资源和教程 |
                | search | 知识库无结果时联网搜索兜底（MCP 联网搜索） |
                | list_knowledge_bases | 列出当前可访问的知识库及文档统计（用户问'知识库有什么'时使用） |
    
                【面试模式说明】
                - TECH_DEEP: 深挖简历技术栈，追问底层原理
                - BEHAVIOR: 行为面试，STAR 法则评估软素质
                - SYSTEM_DESIGN: 系统设计，考察架构能力
                - PRESSURE: 压力面试，追问质疑，测试抗压能力
    
                【执行流程（必须严格遵守）】
                对于用户的每个问题，你必须先判断查询类型，然后按对应流程执行：
    
                ▎查询类型判断
                - 检索型：用户询问面试题、公司信息、薪资数据、学习资源、通用知识等需要外部数据的问题
                - 交互型：模拟面试出题（generate_next_question）、回答分析（analyze_answer）、
                  简历解析（parse_resume）、简历评估（evaluate_resume）、录音转写（transcribe_audio）
                - 复盘型：面试结束后的整场复盘分析、面试诊断、表现总结
    
                ▎检索型查询流程（5 步）
                步骤 1：意图识别
                  - 分析用户问题，判断属于哪种类型：practice（刷题）/ learn（学习）/ company（公司情报）
                    / salary（薪资）/ general（通用）
                  - 在思考中输出意图分类结果
    
                步骤 2：知识库检索
                  - 优先使用 smart_search 或对应的专项搜索工具（search_questions/search_answers/
                    search_resources）检索本地知识库
                  - 若检索到足够信息 → 跳至步骤 4
    
                步骤 3：联网搜索兜底
                  - 若知识库无结果或结果不充分 → 必须使用 search 工具（MCP 联网搜索）获取最新信息
                  - 禁止跳过此步骤：如果知识库不足，你必须联网搜索
    
                步骤 4：信息整合
                  - 将检索结果整合为自然语言回复
                  - 引用来源（标注来自知识库/联网搜索）
                  - 如有必要，对信息进行甄别和筛选
    
                步骤 5：流式输出
                  - 以清晰、结构化、易读的方式输出最终回复
    
                ▎交互型查询流程
                - 直接执行对应工具（generate_next_question / analyze_answer / parse_resume 等）
                - 基于工具返回的结构化数据生成自然语言回复
                - 无需执行知识库检索和联网搜索
    
                ▎面试复盘流程（绝对强制，逐题不可跳过）
                当用户请求面试复盘/诊断/分析时，你必须按以下步骤执行：
    
                步骤 1：梳理问题清单
                  - 从对话历史中提取本场面试的所有问答对（question + answer）
                  - 在思考中列出完整的问题清单，确认共有多少道题
    
                步骤 2：逐题调用 analyze_answer（核心步骤，不可跳过任何一题）
                  - 对清单中的每一道题，逐一调用 analyze_answer(question, answer, mode)
                  - 每道题都必须调用，禁止批量跳过或合并处理
                  - 工具返回每道题的评估指导（含评分维度、亮点/不足/建议模板）
                  - 禁止在未调用 analyze_answer 的情况下，凭记忆直接给某道题打分或写评语
    
                步骤 3：综合诊断（基于工具返回的结构化数据）
                  - 汇总所有题目的 analyze_answer 结果
                  - 提炼共性问题和系统性短板
                  - 基于数据（非猜测）生成综合诊断报告
    
                步骤 4：输出报告
                  - 按题目逐一展示分析结果（评分 + 评语 + 亮点 + 不足 + 改进建议）
                  - 最后给出综合诊断：整体评分、薄弱领域、短期提升建议、长期学习路径
    
                【重要规则】
                1. 工具是分析的权威来源：你只能基于工具返回的结构化数据生成评分、评语和改进建议
                2. 禁止 LLM 越权：不得在未调用 analyze_answer 的情况下，凭自身知识给面试回答打分或诊断
                3. 面试中每 3-4 题给简短反馈，结束时（5-8 题）输出总体评价
                4. 分析结果要客观、具体、可操作
                5. 检索型查询必须遵循"知识库优先 → 不足时联网兜底"的流程，禁止跳过检索直接回复
                6. 绝不暴露内部工具名称和调用过程给用户
                """.stripIndent();
    }

    // ================================================================
    // Permission 配置
    // ================================================================

    private PermissionContextState buildPermissions() {
        return PermissionContextState.builder()
                .mode(PermissionMode.ACCEPT_EDITS)
                .addAllowRule("parse_resume",
                        new PermissionRule("parse_resume", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("evaluate_resume",
                        new PermissionRule("evaluate_resume", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("search_questions",
                        new PermissionRule("search_questions", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("search_answers",
                        new PermissionRule("search_answers", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("analyze_answer",
                        new PermissionRule("analyze_answer", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("transcribe_audio",
                        new PermissionRule("transcribe_audio", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("generate_next_question",
                        new PermissionRule("generate_next_question", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("search_resources",
                        new PermissionRule("search_resources", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("smart_search",
                        new PermissionRule("smart_search", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("list_knowledge_bases",
                        new PermissionRule("list_knowledge_bases", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("search",
                        new PermissionRule("search", null, PermissionBehavior.ALLOW, "联网搜索直接放行"))
                .addDenyRule("delete_user_data",
                        new PermissionRule("delete_user_data", null, PermissionBehavior.DENY, "userSettings"))
                .build();
    }

    /**
     * 显式注册 MCP web-search 服务器到 Toolkit。
     * 不依赖 HarnessAgent 内部的 tools.json 自动加载机制，
     * 直接通过 McpClientBuilder 连接 MCP 服务器并注册工具。
     * 使用 Streamable HTTP 传输协议（与 open-websearch 服务端协议一致）。
     */
    private void registerMcpWebSearch(Toolkit toolkit) {
        String mcpUrl = "http://localhost:3003/mcp";
        try {
            log.info("Connecting to MCP web-search server at {}...", mcpUrl);
            McpClientWrapper mcpClient = McpClientBuilder.create("web-search")
                    .streamableHttpTransport(mcpUrl)
                    .protocolVersions("2024-11-05", "2025-03-26")
                    .httpRequestCustomizer((builder, method, uri, body, ctx) -> {
                        builder.header("Accept", "application/json, text/event-stream");
                    })
                    .timeout(Duration.ofSeconds(60))
                    .initializationTimeout(Duration.ofSeconds(30))
                    .buildAsync()
                    .block();

            if (mcpClient != null) {
                toolkit.registration()
                        .mcpClient(mcpClient)
                        .apply();
                log.info("MCP web-search server registered successfully.");
            } else {
                log.error("MCP web-search client build returned null, agents will lack search capability.");
            }
        } catch (Exception e) {
            log.error("Failed to register MCP web-search server ({}): {}",
                    mcpUrl, e.getMessage(), e);
        }
    }

    /**
     * 按优先级解析用户模型：用户私有 > 用户默认 > 全局默认 > application.yml 兜底。
     */
    private Model resolveModel(String userId) {
        // 1. 查询用户偏好（私有模型优先）
        ModelConfig modelConfig = userModelService.getUserModelConfig(userId);

        // 2. 全局默认
        if (modelConfig == null) {
            modelConfig = modelConfigService.getGlobalDefault();
        }

        // 3. 若通过数据库配置解析到模型，构建 Model 实例
        if (modelConfig != null) {
            String provider = modelConfig.getProvider();
            String modelName = modelConfig.getDefaultModelName();
            String apiKey = apiKeyEncryption.decrypt(modelConfig.getApiKey());
            String asProvider = mapToAgentScopeProvider(provider);
            String modelId = asProvider + ":" + modelName;

            ModelCreationContext context = ModelCreationContext.builder()
                    .apiKey(apiKey)
                    .baseUrl(modelConfig.getBaseUrl())
                    .build();

            try {
                log.debug("Resolving model: {} (original provider: {})", modelId, provider);
                return ModelRegistry.resolve(modelId, context);
            } catch (Exception e) {
                log.warn("Failed to resolve model {} with context, falling back to yml config", modelId, e);
            }
        }

        // 4. 最终兜底：使用 application.yml 中的 agentscope.model.* 配置
        String asProvider = mapToAgentScopeProvider(properties.getModel().getProvider());
        String fallbackModelId = asProvider + ":"
                + properties.getModel().getModelName();
        ModelCreationContext fallbackContext = ModelCreationContext.builder()
                .apiKey(properties.getModel().getApiKey())
                .baseUrl(properties.getModel().getBaseUrl())
                .build();
        log.info("Using fallback model from application.yml: {}", fallbackModelId);
        return ModelRegistry.resolve(fallbackModelId, fallbackContext);
    }

    /**
     * 将 OfferPilot ProviderPreset.providerKey 映射为 AgentScope SPI providerId。
     * deepseek / siliconflow / volcengine 无独立 SPI Provider，统一映射为 openai。
     */
    private String mapToAgentScopeProvider(String provider) {
        if (provider != null && OPENAI_COMPATIBLE_PROVIDERS.contains(provider)) {
            return "openai";
        }
        return provider;
    }

    /**
     * 从缓存中移除 Agent（用于强制重建）。
     */
    public void evictAgent(String userId) {
        agentPool.invalidate(userId);
        log.info("Agent evicted: userId={}", userId);
    }
}
