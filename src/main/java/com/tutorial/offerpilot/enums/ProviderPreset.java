/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.enums;

import lombok.Getter;

/**
 * 系统预设 LLM Provider 配置清单。
 * 共 8 家主流 Provider，分为 OpenAI 兼容阵营（5家）和非 OpenAI 兼容阵营（3家）。
 */
@Getter
public enum ProviderPreset {

    /** 阿里百炼 DashScope — OpenAI 兼容 */
    DASHSCOPE(
            "dashscope",
            "阿里百炼 (DashScope)",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
            ApiFormat.OPENAI,
            AuthHeaderType.BEARER,
            "sk-xxx"
    ),

    /** OpenAI — OpenAI 兼容 */
    OPENAI(
            "openai",
            "OpenAI",
            "https://api.openai.com/v1",
            "https://api.openai.com/v1/models",
            ApiFormat.OPENAI,
            AuthHeaderType.BEARER,
            "sk-xxx"
    ),

    /** DeepSeek — OpenAI 兼容 */
    DEEPSEEK(
            "deepseek",
            "DeepSeek",
            "https://api.deepseek.com",
            "https://api.deepseek.com/models",
            ApiFormat.OPENAI,
            AuthHeaderType.BEARER,
            "sk-xxx"
    ),

    /** 硅基流动 SiliconFlow — OpenAI 兼容 */
    SILICONFLOW(
            "siliconflow",
            "硅基流动 (SiliconFlow)",
            "https://api.siliconflow.cn/v1",
            "https://api.siliconflow.cn/v1/models",
            ApiFormat.OPENAI,
            AuthHeaderType.BEARER,
            "sk-xxx"
    ),

    /** 火山引擎（豆包） — OpenAI 兼容，但需先在控制台创建推理接入点 */
    VOLCENGINE(
            "volcengine",
            "火山引擎 (豆包)",
            "https://ark.cn-beijing.volces.com/api/v3",
            "https://ark.cn-beijing.volces.com/api/v3/models",
            ApiFormat.OPENAI,
            AuthHeaderType.BEARER,
            "apikey-xxx"
    ),

    /** Anthropic Claude — 非 OpenAI 兼容，x-api-key 认证 */
    ANTHROPIC(
            "anthropic",
            "Anthropic (Claude)",
            "https://api.anthropic.com",
            "https://api.anthropic.com/v1/models",
            ApiFormat.ANTHROPIC,
            AuthHeaderType.X_API_KEY,
            "sk-ant-xxx"
    ),

    /** Google Gemini — 非 OpenAI 兼容，x-goog-api-key 认证 */
    GEMINI(
            "gemini",
            "Google Gemini",
            "https://generativelanguage.googleapis.com/v1beta",
            "https://generativelanguage.googleapis.com/v1beta/models",
            ApiFormat.GEMINI,
            AuthHeaderType.X_GOOG_API_KEY,
            "AIza-xxx"
    ),

    /** Ollama 本地部署 — OpenAI 兼容，无需认证 */
    OLLAMA(
            "ollama",
            "Ollama (本地)",
            "http://localhost:11434/v1",
            "http://localhost:11434/v1/models",
            ApiFormat.OPENAI,
            AuthHeaderType.NONE,
            ""
    );

    /** Provider 标识 key */
    private final String providerKey;
    /** 显示名称 */
    private final String displayName;
    /** 默认 Base URL */
    private final String defaultBaseUrl;
    /** 默认模型列表链接 */
    private final String defaultModelListUrl;
    /** API 格式类型 */
    private final ApiFormat apiFormat;
    /** 认证 Header 类型 */
    private final AuthHeaderType authHeaderType;
    /** API Key 模板示例 */
    private final String keyTemplate;

    ProviderPreset(String providerKey, String displayName, String defaultBaseUrl,
                   String defaultModelListUrl, ApiFormat apiFormat,
                   AuthHeaderType authHeaderType, String keyTemplate) {
        this.providerKey = providerKey;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModelListUrl = defaultModelListUrl;
        this.apiFormat = apiFormat;
        this.authHeaderType = authHeaderType;
        this.keyTemplate = keyTemplate;
    }

    /**
     * 根据 provider key 查找预设。
     */
    public static ProviderPreset fromProviderKey(String providerKey) {
        for (ProviderPreset preset : values()) {
            if (preset.providerKey.equalsIgnoreCase(providerKey)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown provider: " + providerKey);
    }

    /** API 格式类型 */
    public enum ApiFormat {
        OPENAI,
        ANTHROPIC,
        GEMINI
    }

    /** 认证 Header 类型 */
    public enum AuthHeaderType {
        BEARER,
        X_API_KEY,
        X_GOOG_API_KEY,
        NONE
    }
}
