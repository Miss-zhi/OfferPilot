/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MilvusConfig {

    @Bean
    public MilvusClientV2 milvusClient(MilvusProperties properties) {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + properties.getHost() + ":" + properties.getPort())
                .dbName(properties.getDatabase())
                .connectTimeoutMs(properties.getConnectTimeoutMs())
                .keepAliveTimeMs(properties.getKeepAliveTimeMs())
                .build();

        log.info("Connecting to Milvus: {}:{}", properties.getHost(), properties.getPort());
        return new MilvusClientV2(config);
    }
}
