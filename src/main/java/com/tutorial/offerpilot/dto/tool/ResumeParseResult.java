/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParseResult {

    private String name;
    private String email;
    private String phone;
    private List<String> education;
    private List<String> projects;
    private List<String> skills;
    private List<String> experience;
}
