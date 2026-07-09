/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.salary;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SalarySearchRequest {

    @NotBlank(message = "公司名称不能为空")
    private String company;

    private String position;
}
