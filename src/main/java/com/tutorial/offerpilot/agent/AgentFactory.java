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
import com.tutorial.offerpilot.service.UserMemoryService;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Agent 构建工厂 + Caffeine 有界缓存池管理。
 * 集成 AgentScope Java v2 HarnessAgent，支持 planMode + taskList + 工具分组。
 */
@Slf4j
@Component
public class AgentFactory {

    private static final int MAX_AGENTS = 500;
    private static final int EVICT_MINUTES = 30;

    private final AgentScopeProperties properties;
    private final UserMemoryService userMemoryService;

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

    /** Caffeine 有界缓存：最多 MAX_AGENTS 个 HarnessAgent，EVICT_MINUTES 分钟未访问自动淘汰 */
    private final Cache<String, HarnessAgent> agentPool = Caffeine.newBuilder()
            .maximumSize(MAX_AGENTS)
            .expireAfterAccess(Duration.ofMinutes(EVICT_MINUTES))
            .removalListener((key, agent, cause) ->
                    log.info("Evicted agent: userId={}, cause={}", key, cause))
            .build();

    public AgentFactory(AgentScopeProperties properties,
                        UserMemoryService userMemoryService,
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
                        SalaryTool salaryTool) {
        this.properties = properties;
        this.userMemoryService = userMemoryService;
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

        // 2. 构建模型标识符
        String modelId = properties.getModel().getProvider() + ":"
                + properties.getModel().getModelName();

        // 3. 系统提示词
        String sysPrompt = buildSystemPrompt();

        // 4. 中间件（每次构建新实例以确保线程安全统计）
        TokenMonitorMiddleware tokenMonitor = new TokenMonitorMiddleware();
        CostControlMiddleware costControl = new CostControlMiddleware();

        // 5. 构建 HarnessAgent
        HarnessAgent agent = HarnessAgent.builder()
                .name("offerpilot_" + userId)
                .sysPrompt(sysPrompt)
                .model(modelId)
                .toolkit(toolkit)
                .middleware(tokenMonitor)
                .middleware(costControl)
                .enablePlanMode()
                .enableTaskList()
                .build();

        log.info("HarnessAgent built: name={}, model={}", agent.getName(), modelId);
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
                You are OfferPilot, a professional AI career coach and interview assistant.

                Your responsibilities:
                1. Help users prepare for job interviews with personalized guidance
                2. Analyze and improve resumes with actionable feedback
                3. Conduct mock interviews and evaluate answers
                4. Provide company-specific interview insights and salary data
                5. Track learning progress and suggest improvement areas

                Guidelines:
                - Be professional, encouraging, and constructive
                - Use tools to search for relevant information before answering
                - For complex tasks, use plan_enter to design a plan first, then execute
                - Maintain context across multi-turn conversations
                - Respect user privacy and confidentiality

                IMPORTANT — Tool output interpretation:
                - When you call `generate_next_question`, it returns guidance (not a ready question).
                  You MUST read the guidance and generate an appropriate interview question yourself,
                  based on the role, category, difficulty, and context provided.
                - When you call `analyze_answer`, it returns the raw Q&A plus evaluation guidance.
                  You MUST generate the actual scores, highlights, weaknesses, and suggestions yourself.
                - When you call `evaluate_resume`, it returns the resume text plus evaluation guidance.
                  You MUST generate the actual score, strengths, weaknesses, and suggestions yourself.
                - For all guidance-based tools, never echo the raw guidance to the user.
                  Always transform it into natural, polished output.
                """.stripIndent();
    }

    /**
     * 从缓存中移除 Agent（用于强制重建）。
     */
    public void evictAgent(String userId) {
        agentPool.invalidate(userId);
        log.info("Agent evicted: userId={}", userId);
    }
}
