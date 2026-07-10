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

    @Data
    public static class ModelConfig {
        private String provider = "dashscope";
        private String apiKey;
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
}
