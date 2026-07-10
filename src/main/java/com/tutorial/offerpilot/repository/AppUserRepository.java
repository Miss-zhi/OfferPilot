/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByUserId(String userId);

    boolean existsByUsername(String username);

    /** 统计引用了指定模型配置的用户数 */
    long countByDefaultModelConfigIdOrPrivateModelConfigId(Long defaultModelConfigId, Long privateModelConfigId);
}
