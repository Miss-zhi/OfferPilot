/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.model.*;
import com.tutorial.offerpilot.service.UserModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户模型偏好管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user/models")
@RequiredArgsConstructor
public class UserModelController {

    private final UserModelService userModelService;

    /** 获取用户可用的模型列表 */
    @GetMapping
    public ApiResponse<List<UserModelResponse>> getAvailableModels(
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Get available models for user: {}", userId);
        return ApiResponse.success(userModelService.getAvailableModels(userId));
    }

    /** 设置用户默认模型 */
    @PutMapping("/default")
    public ApiResponse<Void> setDefaultModel(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody @Valid SetDefaultModelRequest request) {
        String userId = getUserId(currentUser);
        log.info("Set default model: userId={}, configId={}, modelName={}",
                userId, request.getModelConfigId(), request.getModelName());
        userModelService.setDefaultModel(userId, request);
        return ApiResponse.success();
    }

    /** 新增用户私有模型 */
    @PostMapping("/private")
    public ApiResponse<ModelConfigResponse> createPrivateModel(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody @Valid PrivateModelRequest request) {
        String userId = getUserId(currentUser);
        log.info("Create private model: userId={}, provider={}, modelName={}",
                userId, request.getProvider(), request.getModelName());
        return ApiResponse.success(userModelService.createPrivateModel(userId, request));
    }

    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }
}
