/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.model.*;
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
import java.util.stream.Collectors;

/**
 * 模型配置管理服务 — 管理员管理模型池的 CRUD 操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigRepository configRepo;
    private final ModelNameRepository nameRepo;
    private final AppUserRepository userRepo;
    private final ApiKeyEncryption encryption;
    private final ModelListFetcher fetcher;

    /**
     * 获取所有模型配置列表（含关联的模型名称）。
     */
    public List<ModelConfigResponse> listConfigs() {
        List<ModelConfig> configs = configRepo.findByIsPrivateFalse();
        return configs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * 新增模型配置，自动从 Provider API 拉取模型名称列表。
     */
    @Transactional
    public ModelConfigResponse createConfig(CreateModelConfigRequest req) {
        ProviderPreset preset;
        try {
            preset = ProviderPreset.fromProviderKey(req.getProvider());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(),
                    "不支持的 Provider: " + req.getProvider());
        }

        // 根据预设填充配置
        ModelConfig config = new ModelConfig();
        config.setProvider(preset.getProviderKey());
        config.setBaseUrl(preset.getDefaultBaseUrl());
        config.setApiFormat(preset.getApiFormat().name().toLowerCase());
        config.setAuthHeaderType(preset.getAuthHeaderType().name().toLowerCase());
        config.setModelListUrl(
                req.getModelListUrl() != null ? req.getModelListUrl() : preset.getDefaultModelListUrl());
        config.setApiKey(encryption.encrypt(req.getApiKey()));
        config.setIsEnabled(true);
        config.setIsGlobalDefault(false);
        config.setIsPrivate(false);

        config = configRepo.save(config);
        log.info("Model config created: id={}, provider={}", config.getId(), config.getProvider());

        // 拉取模型名称
        List<String> modelNames = fetchAndSaveModelNames(config);
        log.info("Fetched {} model names for config id={}", modelNames.size(), config.getId());

        return toResponse(config, modelNames);
    }

    /**
     * 更新模型配置。
     */
    @Transactional
    public ModelConfigResponse updateConfig(Long id, UpdateModelConfigRequest req) {
        ModelConfig config = configRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(), "模型配置不存在: " + id));

        boolean needRefresh = false;

        if (req.getProvider() != null) {
            ProviderPreset preset;
            try {
                preset = ProviderPreset.fromProviderKey(req.getProvider());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(HttpStatus.BAD_REQUEST.value(),
                        "不支持的 Provider: " + req.getProvider());
            }
            config.setProvider(preset.getProviderKey());
            config.setBaseUrl(preset.getDefaultBaseUrl());
            config.setApiFormat(preset.getApiFormat().name().toLowerCase());
            config.setAuthHeaderType(preset.getAuthHeaderType().name().toLowerCase());
        }
        if (req.getApiKey() != null) {
            config.setApiKey(encryption.encrypt(req.getApiKey()));
        }
        if (req.getModelListUrl() != null && !req.getModelListUrl().equals(config.getModelListUrl())) {
            config.setModelListUrl(req.getModelListUrl());
            needRefresh = true;
        }
        if (req.getDefaultModelName() != null) {
            config.setDefaultModelName(req.getDefaultModelName());
        }
        if (req.getIsEnabled() != null) {
            config.setIsEnabled(req.getIsEnabled());
        }

        config = configRepo.save(config);
        log.info("Model config updated: id={}", config.getId());

        if (needRefresh) {
            fetchAndSaveModelNames(config);
        }

        List<String> modelNames = nameRepo.findByModelConfigId(config.getId())
                .stream().map(ModelName::getModelName).collect(Collectors.toList());

        return toResponse(config, modelNames);
    }

    /**
     * 删除模型配置。
     */
    @Transactional
    public void deleteConfig(Long id) {
        ModelConfig config = configRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(), "模型配置不存在: " + id));

        // 检查是否有用户引用
        long userCount = userRepo.countByDefaultModelConfigIdOrPrivateModelConfigId(id, id);
        if (userCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT.value(),
                    "有 " + userCount + " 位用户正在使用此模型配置，无法删除");
        }

        nameRepo.deleteByModelConfigId(id);
        configRepo.delete(config);
        log.info("Model config deleted: id={}", id);
    }

    /**
     * 重新拉取模型名称列表。
     */
    @Transactional
    public List<String> refreshModels(Long id) {
        ModelConfig config = configRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(), "模型配置不存在: " + id));

        return fetchAndSaveModelNames(config);
    }

    /**
     * 设置为全局默认模型。
     */
    @Transactional
    public ModelConfigResponse setGlobalDefault(Long id, String modelName) {
        ModelConfig config = configRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND.value(), "模型配置不存在: " + id));

        if (!config.getIsEnabled()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "无法将已禁用的模型设为全局默认");
        }

        // 验证 modelName 在可用列表中
        List<ModelName> availableNames = nameRepo.findByModelConfigIdAndIsAvailableTrue(id);
        boolean found = availableNames.stream().anyMatch(n -> n.getModelName().equals(modelName));
        if (!found) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(),
                    "模型名称 '" + modelName + "' 不在可用列表中");
        }

        // 清除现有全局默认
        configRepo.findByIsGlobalDefaultTrue().ifPresent(existing -> {
            existing.setIsGlobalDefault(false);
            configRepo.save(existing);
        });

        config.setIsGlobalDefault(true);
        config.setDefaultModelName(modelName);
        config = configRepo.save(config);

        List<String> modelNames = availableNames.stream().map(ModelName::getModelName).collect(Collectors.toList());
        return toResponse(config, modelNames);
    }

    /**
     * 获取全局默认模型配置。
     */
    public ModelConfig getGlobalDefault() {
        return configRepo.findByIsGlobalDefaultTrue().orElse(null);
    }

    /**
     * 根据 ID 获取模型配置。
     */
    public ModelConfig getById(Long id) {
        return configRepo.findById(id).orElse(null);
    }

    /**
     * 获取 Provider 预设列表。
     */
    public List<ProviderPresetResponse> listProviderPresets() {
        List<ProviderPresetResponse> list = new ArrayList<>();
        for (ProviderPreset preset : ProviderPreset.values()) {
            list.add(ProviderPresetResponse.builder()
                    .provider(preset.getProviderKey())
                    .displayName(preset.getDisplayName())
                    .defaultBaseUrl(preset.getDefaultBaseUrl())
                    .defaultModelListUrl(preset.getDefaultModelListUrl())
                    .apiFormat(preset.getApiFormat().name().toLowerCase())
                    .authHeaderType(preset.getAuthHeaderType().name().toLowerCase())
                    .keyTemplate(preset.getKeyTemplate())
                    .build());
        }
        return list;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从 API 拉取并保存模型名称到数据库。
     */
    private List<String> fetchAndSaveModelNames(ModelConfig config) {
        try {
            String plainApiKey = encryption.decrypt(config.getApiKey());
            List<String> names = fetcher.fetchModelNames(
                    config.getApiFormat(),
                    config.getModelListUrl(),
                    plainApiKey,
                    config.getAuthHeaderType()
            );

            // 清除旧数据
            nameRepo.deleteByModelConfigId(config.getId());

            // 保存新数据
            for (String name : names) {
                ModelName modelName = new ModelName();
                modelName.setModelConfigId(config.getId());
                modelName.setModelName(name);
                modelName.setIsAvailable(true);
                nameRepo.save(modelName);
            }

            return names;
        } catch (Exception e) {
            log.error("Failed to fetch model names for config id={}: {}", config.getId(), e.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY.value(),
                    "拉取模型列表失败: " + e.getMessage());
        }
    }

    private ModelConfigResponse toResponse(ModelConfig config) {
        List<String> modelNames = nameRepo.findByModelConfigId(config.getId())
                .stream().map(ModelName::getModelName).collect(Collectors.toList());
        return toResponse(config, modelNames);
    }

    private ModelConfigResponse toResponse(ModelConfig config, List<String> modelNames) {
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
