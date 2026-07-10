/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

    /** 查询全局默认模型配置 */
    Optional<ModelConfig> findByIsGlobalDefaultTrue();

    /** 查询所有启用的模型配置 */
    List<ModelConfig> findByIsEnabledTrue();

    /** 查询所有非私有的模型配置 */
    List<ModelConfig> findByIsPrivateFalse();

    /** 查询所有启用且非私有的模型配置 */
    List<ModelConfig> findByIsEnabledTrueAndIsPrivateFalse();

    /** 按 provider 查找 */
    List<ModelConfig> findByProvider(String provider);
}
