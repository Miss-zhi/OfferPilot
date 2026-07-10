/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderPresetResponse {

    private String provider;
    private String displayName;
    private String defaultBaseUrl;
    private String defaultModelListUrl;
    private String apiFormat;
    private String authHeaderType;
    private String keyTemplate;
}
