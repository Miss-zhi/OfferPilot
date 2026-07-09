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
}
