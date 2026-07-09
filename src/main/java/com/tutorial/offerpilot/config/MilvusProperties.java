/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.milvus")
public class MilvusProperties {

    private String host = "localhost";
    private int port = 19530;
    private int connectTimeoutMs = 10000;
    private int keepAliveTimeMs = 55000;
    private String database = "offerpilot";
}
