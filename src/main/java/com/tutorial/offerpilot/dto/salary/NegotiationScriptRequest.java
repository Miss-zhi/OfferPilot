/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.salary;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NegotiationScriptRequest {

    @NotBlank(message = "当前 offer 不能为空")
    private String currentOffer;

    private Double targetSalary;
    private String negotiationStyle = "moderate";
}
