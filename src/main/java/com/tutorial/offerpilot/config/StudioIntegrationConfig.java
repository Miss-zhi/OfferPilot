/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.config;

import io.agentscope.core.studio.StudioManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope Studio 集成配置。
 *
 * <p>当 agentscope.studio.enabled=true 时，在应用启动阶段自动初始化 Studio 连接，
 * 包括：注册 Run、建立 WebSocket 连接、注入 StudioMessageHook 和 OpenTelemetry Trace。
 *
 * <p>接入 Studio 后，可以实时查看：
 * <ul>
 *   <li>Agent 消息流（用户输入 → Agent 推理 → 工具调用 → 最终回复）</li>
 *   <li>工具调用参数、耗时、返回结果</li>
 *   <li>LLM 调用 Token 消耗</li>
 *   <li>OpenTelemetry Trace 全链路追踪</li>
 * </ul>
 */
@Slf4j
@Configuration
public class StudioIntegrationConfig {

    private final AgentScopeProperties properties;

    public StudioIntegrationConfig(AgentScopeProperties properties) {
        this.properties = properties;
    }

    /**
     * 应用启动时，若 Studio 已启用，则初始化连接。
     * 初始化失败不会阻止应用启动，仅记录 ERROR 日志。
     */
    @PostConstruct
    public void initStudio() {
        AgentScopeProperties.StudioConfig studio = properties.getStudio();

        if (!studio.isEnabled()) {
            log.info("AgentScope Studio integration is disabled. "
                    + "Set agentscope.studio.enabled=true to enable.");
            return;
        }

        log.info("Initializing AgentScope Studio integration: url={}, project={}",
                studio.getUrl(), studio.getProject());

        try {
            StudioManager.Builder builder = StudioManager.init()
                    .studioUrl(studio.getUrl())
                    .project(studio.getProject());

            if (studio.getTracingUrl() != null && !studio.getTracingUrl().isBlank()) {
                builder.tracingUrl(studio.getTracingUrl());
            }
            if (studio.getMaxRetries() > 0) {
                builder.maxRetries(studio.getMaxRetries());
            }
            if (studio.getReconnectAttempts() > 0) {
                builder.reconnectAttempts(studio.getReconnectAttempts());
            }

            builder.initialize().block();

            log.info("AgentScope Studio integration initialized successfully. "
                    + "Run ID: {}", StudioManager.getConfig().getRunId());
        } catch (Exception e) {
            log.error("Failed to initialize AgentScope Studio integration. "
                    + "Agent will continue to work without Studio monitoring. "
                    + "Cause: {}", e.getMessage(), e);
        }
    }

    /**
     * 应用关闭时释放 Studio 资源（HTTP 连接池、WebSocket）。
     */
    @PreDestroy
    public void shutdownStudio() {
        if (StudioManager.isInitialized()) {
            log.info("Shutting down AgentScope Studio integration...");
            StudioManager.shutdown();
            log.info("AgentScope Studio integration shut down.");
        }
    }
}
