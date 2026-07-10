/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigResponse {

    private Long id;
    private String provider;
    private String baseUrl;
    /** API Key 脱敏显示 */
    private String apiKey;
    private String apiFormat;
    private String authHeaderType;
    private String modelListUrl;
    private String defaultModelName;
    private Boolean isEnabled;
    private Boolean isGlobalDefault;
    private Boolean isPrivate;
    /** 拉取到的模型名称列表 */
    private List<String> modelNames;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private Instant createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private Instant updatedAt;
}
