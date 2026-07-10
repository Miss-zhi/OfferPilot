/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.ModelName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelNameRepository extends JpaRepository<ModelName, Long> {

    /** 按模型配置 ID 查找所有模型名称 */
    List<ModelName> findByModelConfigId(Long modelConfigId);

    /** 按模型配置 ID 删除所有模型名称 */
    void deleteByModelConfigId(Long modelConfigId);

    /** 查询绑定到指定配置 ID 的可用模型名称 */
    List<ModelName> findByModelConfigIdAndIsAvailableTrue(Long modelConfigId);
}
