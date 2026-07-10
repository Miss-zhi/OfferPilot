/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.model;

import lombok.Data;

@Data
public class UpdateModelConfigRequest {

    private String provider;
    private String apiKey;
    private String modelListUrl;
    private String defaultModelName;
    private Boolean isEnabled;
}
