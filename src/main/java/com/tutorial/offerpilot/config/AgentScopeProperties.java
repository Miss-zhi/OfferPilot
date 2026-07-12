/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agentscope")
public class AgentScopeProperties {

    private ModelConfig model = new ModelConfig();
    private AgentConfig agent = new AgentConfig();
    private KnowledgeConfig knowledge = new KnowledgeConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();
    private TranscriptionConfig transcription = new TranscriptionConfig();
    private StudioConfig studio = new StudioConfig();
    private RerankConfig rerank = new RerankConfig();

    @Data
    public static class ModelConfig {
        private String provider = "dashscope";
        private String apiKey;
        private String baseUrl;
        private String modelName = "qwen-max";
        private Double temperature = 0.7;
        private Integer maxTokens = 4096;
    }

    @Data
    public static class AgentConfig {
        private String workspace = "./workspace";
        private String stateStore = "redis";
        private CompactionConfig compaction = new CompactionConfig();
    }

    @Data
    public static class CompactionConfig {
        private boolean enabled = true;
        private int maxTokens = 8000;
    }

    @Data
    public static class KnowledgeConfig {
        private String basePath = "./workspace/knowledge";
        private String embeddingModel = "text-embedding-v3";
        private int chunkSize = 500;
        private int chunkOverlap = 50;
        private int topK = 5;
        private boolean autoInit = true;
    }

    /**
     * Embedding 独立配置 — 与 LLM Model 解耦。
     * 当 LLM Provider 切换为 DeepSeek 等不支持 Embedding 的服务商时，
     * 可通过此配置单独指定 Embedding Provider（默认 DashScope），避免共用 api-key。
     */
    @Data
    public static class EmbeddingConfig {
        /** Embedding Provider，默认 dashscope */
        private String provider = "dashscope";
        /** Embedding API Key，未配置时回退使用 agentscope.model.api-key */
        private String apiKey;
        /** Embedding API Base URL */
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    }

    /**
     * 录音转写独立配置 — 与 LLM Model 解耦。
     * 默认使用 DashScope Paraformer（OpenAI 兼容 /v1/audio/transcriptions）。
     * 未配置 api-key 时自动回退使用 agentscope.model.api-key。
     */
    @Data
    public static class TranscriptionConfig {
        /** 转写模型，默认 paraformer-v2 */
        private String model = "paraformer-v2";
        /** 转写 API Key，未配置时回退使用 agentscope.model.api-key */
        private String apiKey;
        /** 转写 API Base URL（OpenAI 兼容端点） */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }

    /**
     * AgentScope Studio 配置 — 实时监控 Agent 调用与 Trace。
     * 接入 Studio 后可实时查看 Agent 消息、工具调用参数/耗时/返回结果、Token 消耗等。
     * 默认关闭，开发/调试时通过 agentscope.studio.enabled=true 开启。
     */
    @Data
    public static class StudioConfig {
        /** 是否启用 Studio 集成，默认关闭 */
        private boolean enabled = false;
        /** Studio 服务地址（AgentScope Studio 前端 + 后端） */
        private String url = "http://localhost:8000";
        /** 项目名称，在 Studio 中标识本项目 */
        private String project = "OfferPilot";
        /** Trace 端点，默认为 {url}/v1/traces */
        private String tracingUrl;
        /** HTTP 请求最大重试次数 */
        private int maxRetries = 3;
        /** WebSocket 重连最大尝试次数 */
        private int reconnectAttempts = 3;
    }

    /**
     * Rerank 独立配置 — 与 LLM Model 解耦。
     * 用于检索后精排阶段，对多路召回结果进行语义相关性重排序。
     * 默认使用 DashScope qwen3-rerank（OpenAI 兼容端点）。
     * 未配置 api-key 时自动回退使用 agentscope.model.api-key。
     */
    @Data
    public static class RerankConfig {
        /** 是否启用 Rerank 精排，默认启用；关闭后 RRF 融合结果直接返回 */
        private boolean enabled = true;
        /** Rerank API Key，未配置时回退使用 agentscope.model.api-key */
        private String apiKey;
        /** Rerank 模型名称，默认 qwen3-rerank */
        private String modelName = "qwen3-rerank";
        /** Rerank API Base URL（OpenAI 兼容端点） */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-api/v1/reranks";
        /** 精排后保留数量 */
        private int topN = 5;
        /** 最低相关性分数阈值，低于此值的结果将被过滤 */
        private double scoreThreshold = 0.0;
        /** HTTP 连接超时（秒） */
        private int connectTimeout = 10;
        /** HTTP 读取超时（秒） */
        private int readTimeout = 30;
    }
}
