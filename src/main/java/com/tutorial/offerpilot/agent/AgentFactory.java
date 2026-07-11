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
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

    /** 工具实例 — 所有 11 个工具类 */
    private final AnswerAnalyzeTool answerAnalyzeTool;
    private final AnswerSearchTool answerSearchTool;
    private final AudioTranscribeTool audioTranscribeTool;
    private final CompanySearchTool companySearchTool;
    private final MockInterviewTool mockInterviewTool;
    private final ProgressTrackTool progressTrackTool;
    private final QuestionSearchTool questionSearchTool;
    private final ResourceSearchTool resourceSearchTool;
    private final ResumeEvaluateTool resumeEvaluateTool;
    private final ResumeParseTool resumeParseTool;
    private final SalaryTool salaryTool;
    private final SmartSearchTool smartSearchTool;
    private final PriorityRankTool priorityRankTool;

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
                        CompanySearchTool companySearchTool,
                        MockInterviewTool mockInterviewTool,
                        ProgressTrackTool progressTrackTool,
                        QuestionSearchTool questionSearchTool,
                        ResourceSearchTool resourceSearchTool,
                        ResumeEvaluateTool resumeEvaluateTool,
                        ResumeParseTool resumeParseTool,
                        SalaryTool salaryTool,
                        SmartSearchTool smartSearchTool,
                        PriorityRankTool priorityRankTool) {
        this.properties = properties;
        this.userMemoryService = userMemoryService;
        this.userModelService = userModelService;
        this.modelConfigService = modelConfigService;
        this.apiKeyEncryption = apiKeyEncryption;
        this.answerAnalyzeTool = answerAnalyzeTool;
        this.answerSearchTool = answerSearchTool;
        this.audioTranscribeTool = audioTranscribeTool;
        this.companySearchTool = companySearchTool;
        this.mockInterviewTool = mockInterviewTool;
        this.progressTrackTool = progressTrackTool;
        this.questionSearchTool = questionSearchTool;
        this.resourceSearchTool = resourceSearchTool;
        this.resumeEvaluateTool = resumeEvaluateTool;
        this.resumeParseTool = resumeParseTool;
        this.salaryTool = salaryTool;
        this.smartSearchTool = smartSearchTool;
        this.priorityRankTool = priorityRankTool;
    }

    /**
     * 获取或创建 HarnessAgent 实例。
     */
    public HarnessAgent getOrCreateAgent(String userId) {
        return agentPool.get(userId, this::buildAgent);
    }

    private HarnessAgent buildAgent(String userId) {
        log.info("Building HarnessAgent for userId={}", userId);

        // 1. 构建 Toolkit — 4 个分组 + 注册所有工具
        Toolkit toolkit = buildToolkit();

        // 2. 按优先级选择模型: 用户私有 > 用户默认 > 全局默认 > application.yml 兜底
        Model model = resolveModel(userId);

        // 3. 系统提示词（调度中心）
        String sysPrompt = buildSystemPrompt();

        // 4. 中间件（每次构建新实例以确保线程安全统计）
        TokenMonitorMiddleware tokenMonitor = new TokenMonitorMiddleware();
        CostControlMiddleware costControl = new CostControlMiddleware();

        // 5. Permission
        PermissionContextState perms = buildPermissions();

        // 6. 定义 7 个子 Agent
        SubagentDeclaration resumeAgent = buildResumeAgent();
        SubagentDeclaration techEvalAgent = buildTechEvalAgent();
        SubagentDeclaration exprEvalAgent = buildExprEvalAgent();
        SubagentDeclaration mockAgent = buildMockAgent();
        SubagentDeclaration companyAgent = buildCompanyAgent();
        SubagentDeclaration studyAgent = buildStudyAgent();
        SubagentDeclaration salaryAgent = buildSalaryAgent();

        // 7. 构建 HarnessAgent
        HarnessAgent agent = HarnessAgent.builder()
                .name("offerpilot_" + userId)
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(toolkit)
                .workspace(Path.of("./workspace"))
                .subagent(resumeAgent)
                .subagent(techEvalAgent)
                .subagent(exprEvalAgent)
                .subagent(mockAgent)
                .subagent(companyAgent)
                .subagent(studyAgent)
                .subagent(salaryAgent)
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
     * 构建 Toolkit，创建 4 个工具分组并注册所有 11 个工具 + registerMetaTool。
     *
     * <pre>
     *     分组 1: knowledge_retrieval — 知识检索
     *     分组 2: resume_analysis      — 简历分析
     *     分组 3: interview            — 面试
     *     分组 4: utility              — 通用工具
     * </pre>
     */
    private Toolkit buildToolkit() {
        Toolkit toolkit = new Toolkit();

        // ---- 创建 4 个工具分组 ----
        toolkit.createToolGroup(
                "knowledge_retrieval",
                "知识检索工具组 — 搜索面试题库、优秀答案、公司面经、学习资源");
        toolkit.createToolGroup(
                "resume_analysis",
                "简历分析工具组 — 简历解析和简历评估");
        toolkit.createToolGroup(
                "interview",
                "面试工具组 — 模拟面试、回答分析、录音转写");
        toolkit.createToolGroup(
                "utility",
                "通用工具组 — 进度追踪、薪资查询");

        // ---- 分组 1: knowledge_retrieval ----
        toolkit.registration()
                .tool(answerSearchTool)
                .group("knowledge_retrieval")
                .apply();
        toolkit.registration()
                .tool(questionSearchTool)
                .group("knowledge_retrieval")
                .apply();
        toolkit.registration()
                .tool(companySearchTool)
                .group("knowledge_retrieval")
                .apply();
        toolkit.registration()
                .tool(resourceSearchTool)
                .group("knowledge_retrieval")
                .apply();
        toolkit.registration()
                .tool(smartSearchTool)
                .group("knowledge_retrieval")
                .apply();

        // ---- 分组 2: resume_analysis ----
        toolkit.registration()
                .tool(resumeParseTool)
                .group("resume_analysis")
                .apply();
        toolkit.registration()
                .tool(resumeEvaluateTool)
                .group("resume_analysis")
                .apply();

        // ---- 分组 3: interview ----
        toolkit.registration()
                .tool(mockInterviewTool)
                .group("interview")
                .apply();
        toolkit.registration()
                .tool(answerAnalyzeTool)
                .group("interview")
                .apply();
        toolkit.registration()
                .tool(audioTranscribeTool)
                .group("interview")
                .apply();

        // ---- 分组 4: utility ----
        toolkit.registration()
                .tool(progressTrackTool)
                .group("utility")
                .apply();
        toolkit.registration()
                .tool(salaryTool)
                .group("utility")
                .apply();
        toolkit.registration()
                .tool(priorityRankTool)
                .group("utility")
                .apply();

        // ---- 注册 MCP web-search 工具 ----
        registerMcpWebSearch(toolkit);

        // ---- 注册元工具 ----
        toolkit.registerMetaTool();

        log.info("Toolkit built: groups={}, tools={}",
                toolkit.getActiveGroups().size(),
                toolkit.getToolNames().size());

        return toolkit;
    }

    /**
     * 构建系统提示词，定义 OfferPilot 助手的角色和行为。
     */
    private String buildSystemPrompt() {
        return """
                You are OfferPilot 面试诊断助手的调度中心。

                【你的唯一职责】理解用户需求，分派任务给子 Agent，整合结果回复用户。
                【严禁行为】你绝对不能直接调用任何业务工具（search_questions、analyze_answer、
                           parse_resume 等）。你的工具箱中只有 spawn/resume_subagent 和
                           search。所有业务操作必须通过子 Agent 完成。

                子 Agent 分派指南：
                - 简历分析/优化 → spawn resume_coach
                - 面试回答分析/评分 → spawn tech_evaluator + expression_evaluator（并行）
                - 模拟面试练习 → spawn mock_interviewer
                - 公司面试情报 → spawn company_researcher
                - 学习计划/进度 → spawn study_planner
                - 薪资查询/offer对比/谈判策略 → spawn salary_advisor
                - 通用知识问答（无匹配子Agent时） → 调用 search 从互联网获取信息

                调度规则：
                1. 你只能使用 spawn（创建子Agent任务）和 resume（恢复子Agent任务）。
                2. 子 Agent 返回结果后，用自然语言整合回复用户，不暴露内部调度过程。
                3. 如果用户意图跨越多个子Agent领域，按优先级依次 spawn。
                4. 绝不绕过子Agent直接调用业务工具——即使你知道答案也要走子Agent流程。

                IMPORTANT — Tool output interpretation:
                - When you call `generate_next_question`, it returns guidance (not a ready question).
                - When you call `analyze_answer`, it returns the raw Q&A plus evaluation guidance.
                - When you call `evaluate_resume`, it returns the resume text plus evaluation guidance.
                - For all guidance-based tools, never echo the raw guidance to the user.
                """.stripIndent();
    }

    // ================================================================
    // 子 Agent 声明
    // ================================================================

    private SubagentDeclaration buildResumeAgent() {
        return SubagentDeclaration.builder()
                .name("resume_coach")
                .description("简历诊断顾问。当用户上传简历、要求简历优化时调用。")
                .inlineAgentsBody("""
                        你是一个资深 HR 顾问，有 10 年简历筛选经验。
                        你的职责：
                        1. 调用 parse_resume 解析简历
                        2. 调用 evaluate_resume 评估质量
                        3. 如果有目标 JD，检索该岗位高频考点，对比简历技能覆盖度
                        4. 给出具体、可操作的优化建议
                        语气专业但友善。""")
                .tools(List.of("parse_resume", "evaluate_resume", "search_questions"))
                .build();
    }

    private SubagentDeclaration buildTechEvalAgent() {
        return SubagentDeclaration.builder()
                .name("tech_evaluator")
                .description("技术评估专家。当需要分析候选人的技术面试回答时调用。")
                .inlineAgentsBody("""
                        你是一个严格但公正的技术面试官，阿里 P7 级别。
                        1. 调用 search_answers 检索优秀答案和评分标准
                        2. 调用 analyze_answer 评估候选人的回答
                        3. 逐条对比，指出覆盖了哪些得分点、遗漏了哪些
                        评估要客观，好的地方说好，不好的地方说不好。""")
                .tools(List.of("search_answers", "analyze_answer", "search_questions"))
                .build();
    }

    private SubagentDeclaration buildExprEvalAgent() {
        return SubagentDeclaration.builder()
                .name("expression_evaluator")
                .description("表达评估专家。当需要分析候选人的表达逻辑、沟通技巧时调用。")
                .inlineAgentsBody("""
                        你是一个沟通技巧教练。
                        1. 分析回答的结构——是否有清晰的总分结构、是否用了 STAR 法则
                        2. 检查口头禅和废话密度
                        3. 评估时间分配——核心观点是否在前 30 秒内给出
                        关注表达方式，不关注技术内容。""")
                .tools(List.of("analyze_answer"))
                .build();
    }

    private SubagentDeclaration buildMockAgent() {
        return SubagentDeclaration.builder()
                .name("mock_interviewer")
                .description("模拟面试官。当用户想进行模拟面试练习时调用。")
                .inlineAgentsBody("""
                        你是面试官。面试开始前，请先确认面试模式：
                        - 技术深挖 (TECH_DEEP)：适合技术岗，深挖项目经历和底层原理
                        - 行为面试 (BEHAVIOR)：评估软素质和沟通能力
                        - 系统设计 (SYSTEM_DESIGN)：考察架构设计能力
                        - 压力面试 (PRESSURE)：测试抗压能力和临场反应

                        如果没有明确指定，根据用户岗位默认选择：
                        - 后端/算法/数据 → TECH_DEEP
                        - 产品/运营 → BEHAVIOR
                        - 架构师/高级开发 → SYSTEM_DESIGN

                        每轮流程：
                        1. 调用 generate_next_question(mode, context, resume_text) 获取出题指导
                        2. 用自然语言提问
                        3. 用户回答后调用 analyze_answer(question, answer, mode) 分析
                        4. 压力模式下根据 followUpGuidance 追问
                        5. 每 3-4 题给简短反馈
                        6. 面试结束（5-8 题）输出总体评价""")
                .tools(List.of("generate_next_question", "search_answers", "analyze_answer"))
                .build();
    }

    private SubagentDeclaration buildCompanyAgent() {
        return SubagentDeclaration.builder()
                .name("company_researcher")
                .description("公司面试情报调研员。当用户想了解目标公司的面试情况时调用。")
                .inlineAgentsBody("""
                        你是一个面试情报分析师。
                        1. 调用 search_company_interviews 检索目标公司的面试情报
                        2. 调用 search_questions 检索高频考点的具体题目
                        3. 如果以上两个工具返回的结果不足或为空，
                           调用 search 从互联网搜索最新的面试经验
                        4. 整合成'面试情报卡'，标注数据来源（知识库/互联网）
                        信息要准确，标注数据来源和时间。""")
                .tools(List.of("search_company_interviews", "search_questions", "search"))
                .build();
    }

    private SubagentDeclaration buildStudyAgent() {
        return SubagentDeclaration.builder()
                .name("study_planner")
                .description("学习规划师。当用户想制定学习计划、查看学习进度时调用。")
                .inlineAgentsBody("""
                        你是一个学习计划规划师。
                        1. 调用 track_progress 查看用户的学习数据
                        2. 调用 prioritize_weaknesses 对薄弱知识点进行量化优先级排序
                        3. 按优先级从高到低安排学习顺序
                        4. 调用 search_resources 匹配学习资源
                        5. 调用 search_questions 检索高频考题
                        6. 如果知识库中学习资源不足，调用 search 从互联网搜索
                           相关教程、文档和练习材料
                        7. 生成周学习计划
                        计划要实际可执行，每天 1-2 小时为宜。""")
                .tools(List.of("track_progress", "prioritize_weaknesses", "search_resources", "search_questions", "search"))
                .build();
    }

    private SubagentDeclaration buildSalaryAgent() {
        return SubagentDeclaration.builder()
                .name("salary_advisor")
                .description("薪资谈判顾问。当用户拿到 offer、需要对比分析、薪资谈判策略时调用。")
                .inlineAgentsBody("""
                        你是一个资深 HR 和职业规划顾问，有 15 年招聘和薪资谈判经验。
                        你的职责：
                        1. 调用 search_salary 检索目标公司+岗位的薪资范围
                        2. 如果本地薪资数据库无记录，调用 search 从互联网搜索
                           该公司和岗位的最新薪资行情
                        3. 调用 compare_offers 对比多个 offer 的综合待遇
                        4. 调用 generate_negotiation_script 生成谈判话术
                        5. 结合市场数据给出客观建议
                        薪资信息敏感，回复要专业、客观、有数据支撑。""")
                .tools(List.of("search_salary", "compare_offers", "generate_negotiation_script", "search"))
                .build();
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
                .addAllowRule("search_company_interviews",
                        new PermissionRule("search_company_interviews", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("analyze_answer",
                        new PermissionRule("analyze_answer", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("transcribe_audio",
                        new PermissionRule("transcribe_audio", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("generate_next_question",
                        new PermissionRule("generate_next_question", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("search_resources",
                        new PermissionRule("search_resources", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("track_progress",
                        new PermissionRule("track_progress", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("prioritize_weaknesses",
                        new PermissionRule("prioritize_weaknesses", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("search_salary",
                        new PermissionRule("search_salary", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("compare_offers",
                        new PermissionRule("compare_offers", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("generate_negotiation_script",
                        new PermissionRule("generate_negotiation_script", null, PermissionBehavior.ALLOW, "userSettings"))
                .addAllowRule("smart_search",
                        new PermissionRule("smart_search", null, PermissionBehavior.ALLOW, "userSettings"))
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
        String mcpUrl = "http://localhost:3000/mcp";
        try {
            log.info("Connecting to MCP web-search server at {}...", mcpUrl);
            McpClientWrapper mcpClient = McpClientBuilder.create("web-search")
                    .streamableHttpTransport(mcpUrl)
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
