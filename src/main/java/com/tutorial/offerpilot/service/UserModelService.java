/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.model.*;
import com.tutorial.offerpilot.entity.AppUser;
import com.tutorial.offerpilot.entity.ModelConfig;
import com.tutorial.offerpilot.entity.ModelName;
import com.tutorial.offerpilot.enums.ProviderPreset;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.repository.AppUserRepository;
import com.tutorial.offerpilot.repository.ModelConfigRepository;
import com.tutorial.offerpilot.repository.ModelNameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 用户模型偏好管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserModelService {

    private final AppUserRepository userRepo;
    private final ModelConfigRepository configRepo;
    private final ModelNameRepository nameRepo;
    private final ApiKeyEncryption encryption;
    private final ModelListFetcher fetcher;

    /**
     * 获取用户可用的模型列表（仅展示管理员启用且可用的模型）。
     */
    public List<UserModelResponse> getAvailableModels(String userId) {
        List<ModelConfig> enabledConfigs = configRepo.findByIsEnabledTrueAndIsPrivateFalse();
        AppUser user = userRepo.findByUsername(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(), "用户不存在: " + userId));

        // 获取全局默认配置
        ModelConfig globalDefault = configRepo.findByIsGlobalDefaultTrue().orElse(null);
        Long globalDefaultId = globalDefault != null ? globalDefault.getId() : null;

        List<UserModelResponse> result = new ArrayList<>();
        for (ModelConfig config : enabledConfigs) {
            List<ModelName> availableNames = nameRepo.findByModelConfigIdAndIsAvailableTrue(config.getId());
            for (ModelName mn : availableNames) {
                result.add(UserModelResponse.builder()
                        .configId(config.getId())
                        .provider(config.getProvider())
                        .modelName(mn.getModelName())
                        .isGlobalDefault(config.getId().equals(globalDefaultId)
                                && mn.getModelName().equals(globalDefault.getDefaultModelName()))
                        .isUserDefault(config.getId().equals(user.getDefaultModelConfigId()))
                        .build());
            }
        }

        return result;
    }

    /**
     * 设置用户默认模型。
     */
    @Transactional
    public void setDefaultModel(String userId, SetDefaultModelRequest req) {
        AppUser user = userRepo.findByUsername(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(), "用户不存在: " + userId));

        // 验证模型配置存在且已启用
        ModelConfig config = configRepo.findById(req.getModelConfigId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(),
                        "模型配置不存在: " + req.getModelConfigId()));

        if (!config.getIsEnabled()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "该模型已被禁用");
        }

        // 验证模型名称在可用列表中
        List<ModelName> availableNames = nameRepo.findByModelConfigIdAndIsAvailableTrue(req.getModelConfigId());
        boolean found = availableNames.stream().anyMatch(n -> n.getModelName().equals(req.getModelName()));
        if (!found) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(),
                    "模型名称 '" + req.getModelName() + "' 不可用");
        }

        user.setDefaultModelConfigId(req.getModelConfigId());
        userRepo.save(user);
        log.info("User default model set: userId={}, configId={}, modelName={}",
                userId, req.getModelConfigId(), req.getModelName());
    }

    /**
     * 用户新增私有模型配置。
     */
    @Transactional
    public ModelConfigResponse createPrivateModel(String userId, PrivateModelRequest req) {
        AppUser user = userRepo.findByUsername(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(), "用户不存在: " + userId));

        ProviderPreset preset;
        try {
            preset = ProviderPreset.fromProviderKey(req.getProvider());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "不支持的 Provider: " + req.getProvider());
        }

        ModelConfig config = new ModelConfig();
        config.setProvider(preset.getProviderKey());
        config.setBaseUrl(preset.getDefaultBaseUrl());
        config.setApiFormat(preset.getApiFormat().name().toLowerCase());
        config.setAuthHeaderType(preset.getAuthHeaderType().name().toLowerCase());
        config.setModelListUrl(
                req.getModelListUrl() != null ? req.getModelListUrl() : preset.getDefaultModelListUrl());
        config.setApiKey(encryption.encrypt(req.getApiKey()));
        config.setDefaultModelName(req.getModelName());
        config.setIsEnabled(true);
        config.setIsGlobalDefault(false);
        config.setIsPrivate(true);
        config.setCreateBy(user.getUsername());
        config.setUpdateBy(user.getUsername());

        config = configRepo.save(config);
        log.info("Private model config created: userId={}, configId={}", userId, config.getId());

        // 拉取模型名称并验证 req.modelName 在列表中
        try {
            List<String> names = fetchAndSaveModelNames(config);
            if (!names.contains(req.getModelName())) {
                log.warn("Requested modelName '{}' not in fetched list, but allowing it anyway", req.getModelName());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch model names for private config, but allowing it", e);
        }

        // 自动设为用户默认模型
        user.setPrivateModelConfigId(config.getId());
        userRepo.save(user);

        return buildPrivateModelResponse(config);
    }

    /**
     * 获取用户当前使用的模型配置（优先级：私有 > 默认 > null）。
     */
    public ModelConfig getUserModelConfig(String userId) {
        AppUser user = userRepo.findByUsername(userId).orElse(null);
        if (user == null) return null;

        // 优先使用私有模型
        if (user.getPrivateModelConfigId() != null) {
            return configRepo.findById(user.getPrivateModelConfigId()).orElse(null);
        }

        // 使用用户默认模型
        if (user.getDefaultModelConfigId() != null) {
            return configRepo.findById(user.getDefaultModelConfigId()).orElse(null);
        }

        return null;
    }

    // ========== 私有辅助方法 ==========

    private List<String> fetchAndSaveModelNames(ModelConfig config) {
        String plainApiKey = encryption.decrypt(config.getApiKey());
        List<String> names = fetcher.fetchModelNames(
                config.getApiFormat(),
                config.getModelListUrl(),
                plainApiKey,
                config.getAuthHeaderType()
        );

        nameRepo.deleteByModelConfigId(config.getId());
        for (String name : names) {
            ModelName mn = new ModelName();
            mn.setModelConfigId(config.getId());
            mn.setModelName(name);
            mn.setIsAvailable(true);
            nameRepo.save(mn);
        }

        return names;
    }

    private ModelConfigResponse buildPrivateModelResponse(ModelConfig config) {
        List<String> modelNames = nameRepo.findByModelConfigId(config.getId())
                .stream().map(ModelName::getModelName)
                .toList();

        return ModelConfigResponse.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .apiKey(ApiKeyEncryption.mask(encryption.decrypt(config.getApiKey())))
                .apiFormat(config.getApiFormat())
                .authHeaderType(config.getAuthHeaderType())
                .modelListUrl(config.getModelListUrl())
                .defaultModelName(config.getDefaultModelName())
                .isEnabled(config.getIsEnabled())
                .isGlobalDefault(config.getIsGlobalDefault())
                .isPrivate(config.getIsPrivate())
                .modelNames(modelNames)
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
