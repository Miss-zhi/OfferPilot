/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.model.*;
import com.tutorial.offerpilot.service.ModelConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员模型配置管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    /** 获取所有模型配置列表 */
    @GetMapping
    public ApiResponse<List<ModelConfigResponse>> listConfigs() {
        log.info("List model configs");
        return ApiResponse.success(modelConfigService.listConfigs());
    }

    /** 新增模型配置（含拉取模型名称） */
    @PostMapping
    public ApiResponse<ModelConfigResponse> createConfig(@RequestBody @Valid CreateModelConfigRequest request) {
        log.info("Create model config: provider={}", request.getProvider());
        return ApiResponse.success(modelConfigService.createConfig(request));
    }

    /** 更新模型配置 */
    @PutMapping("/{id}")
    public ApiResponse<ModelConfigResponse> updateConfig(
            @PathVariable Long id,
            @RequestBody UpdateModelConfigRequest request) {
        log.info("Update model config: id={}", id);
        return ApiResponse.success(modelConfigService.updateConfig(id, request));
    }

    /** 删除模型配置 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable Long id) {
        log.info("Delete model config: id={}", id);
        modelConfigService.deleteConfig(id);
        return ApiResponse.success();
    }

    /** 重新拉取模型名称 */
    @PostMapping("/{id}/refresh-models")
    public ApiResponse<List<String>> refreshModels(@PathVariable Long id) {
        log.info("Refresh models for config: id={}", id);
        return ApiResponse.success(modelConfigService.refreshModels(id));
    }

    /** 设置全局默认模型 */
    @PutMapping("/{id}/set-global-default")
    public ApiResponse<ModelConfigResponse> setGlobalDefault(
            @PathVariable Long id,
            @RequestBody @Valid SetGlobalDefaultRequest request) {
        log.info("Set global default model: id={}, modelName={}", id, request.getModelName());
        return ApiResponse.success(modelConfigService.setGlobalDefault(id, request.getModelName()));
    }

    /** 获取系统预设 Provider 列表 */
    @GetMapping("/provider-presets")
    public ApiResponse<List<ProviderPresetResponse>> listProviderPresets() {
        log.info("List provider presets");
        return ApiResponse.success(modelConfigService.listProviderPresets());
    }
}
