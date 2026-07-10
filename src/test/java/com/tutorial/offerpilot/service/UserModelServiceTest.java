/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.model.*;
import com.tutorial.offerpilot.entity.AppUser;
import com.tutorial.offerpilot.entity.ModelConfig;
import com.tutorial.offerpilot.entity.ModelName;
import com.tutorial.offerpilot.enums.UserRole;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.repository.AppUserRepository;
import com.tutorial.offerpilot.repository.ModelConfigRepository;
import com.tutorial.offerpilot.repository.ModelNameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserModelService 单元测试")
class UserModelServiceTest {

    @Mock private AppUserRepository userRepo;
    @Mock private ModelConfigRepository configRepo;
    @Mock private ModelNameRepository nameRepo;
    @Mock private ApiKeyEncryption encryption;
    @Mock private ModelListFetcher fetcher;

    @InjectMocks
    private UserModelService service;

    private AppUser testUser;
    private ModelConfig enabledConfig;

    @BeforeEach
    void setUp() {
        testUser = new AppUser();
        testUser.setUsername("testuser");
        testUser.setRole(UserRole.USER);

        enabledConfig = new ModelConfig();
        enabledConfig.setId(1L);
        enabledConfig.setProvider("dashscope");
        enabledConfig.setIsEnabled(true);
        enabledConfig.setIsGlobalDefault(false);
        enabledConfig.setIsPrivate(false);
        enabledConfig.setApiKey("encrypted:sk-test");

        lenient().when(encryption.encrypt(anyString())).thenAnswer(inv -> "encrypted:" + inv.getArgument(0));
        lenient().when(encryption.decrypt(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.startsWith("encrypted:") ? s.substring(10) : "decrypted";
        });
    }

    @Nested
    @DisplayName("getAvailableModels")
    class GetAvailableModelsTests {

        @Test
        @DisplayName("返回启用的模型列表，标记全局默认和用户默认")
        void shouldReturnAvailableModelsWithFlags() {
            enabledConfig.setIsGlobalDefault(true);
            enabledConfig.setDefaultModelName("qwen-max");

            testUser.setDefaultModelConfigId(1L);

            when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(configRepo.findByIsEnabledTrueAndIsPrivateFalse()).thenReturn(List.of(enabledConfig));
            when(configRepo.findByIsGlobalDefaultTrue()).thenReturn(Optional.of(enabledConfig));
            when(nameRepo.findByModelConfigIdAndIsAvailableTrue(1L))
                    .thenReturn(List.of(
                            createModelName(1L, "qwen-max"),
                            createModelName(1L, "qwen-plus")
                    ));

            List<UserModelResponse> result = service.getAvailableModels("testuser");

            assertNotNull(result);
            assertEquals(2, result.size());

            UserModelResponse globalModel = result.stream()
                    .filter(UserModelResponse::getIsGlobalDefault).findFirst().orElse(null);
            assertNotNull(globalModel);
            assertEquals("qwen-max", globalModel.getModelName());

            UserModelResponse userModel = result.stream()
                    .filter(UserModelResponse::getIsUserDefault).findFirst().orElse(null);
            assertNotNull(userModel);
        }

        @Test
        @DisplayName("用户不存在时抛异常")
        void shouldThrowWhenUserNotFound() {
            when(userRepo.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> service.getAvailableModels("unknown"));
        }
    }

    @Nested
    @DisplayName("setDefaultModel")
    class SetDefaultModelTests {

        @Test
        @DisplayName("设置默认模型成功")
        void shouldSetDefaultModel() {
            SetDefaultModelRequest req = new SetDefaultModelRequest();
            req.setModelConfigId(1L);
            req.setModelName("qwen-max");

            when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(configRepo.findById(1L)).thenReturn(Optional.of(enabledConfig));
            when(nameRepo.findByModelConfigIdAndIsAvailableTrue(1L))
                    .thenReturn(List.of(createModelName(1L, "qwen-max")));

            assertDoesNotThrow(() -> service.setDefaultModel("testuser", req));
            assertEquals(1L, testUser.getDefaultModelConfigId());
            verify(userRepo).save(testUser);
        }

        @Test
        @DisplayName("已禁用的模型不能设为默认")
        void shouldNotSetDisabledModel() {
            enabledConfig.setIsEnabled(false);
            SetDefaultModelRequest req = new SetDefaultModelRequest();
            req.setModelConfigId(1L);
            req.setModelName("qwen-max");

            when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(configRepo.findById(1L)).thenReturn(Optional.of(enabledConfig));

            assertThrows(BusinessException.class, () -> service.setDefaultModel("testuser", req));
        }
    }

    @Nested
    @DisplayName("getUserModelConfig")
    class GetUserModelConfigTests {

        @Test
        @DisplayName("优先返回私有模型")
        void shouldReturnPrivateModelFirst() {
            ModelConfig privateConfig = new ModelConfig();
            privateConfig.setId(99L);
            privateConfig.setIsPrivate(true);

            testUser.setPrivateModelConfigId(99L);
            testUser.setDefaultModelConfigId(1L);

            when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(configRepo.findById(99L)).thenReturn(Optional.of(privateConfig));

            ModelConfig result = service.getUserModelConfig("testuser");

            assertNotNull(result);
            assertEquals(99L, result.getId());
            verify(configRepo, never()).findById(1L);
        }

        @Test
        @DisplayName("无私有模型时返回用户默认模型")
        void shouldReturnDefaultModelWhenNoPrivate() {
            testUser.setDefaultModelConfigId(1L);

            when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(configRepo.findById(1L)).thenReturn(Optional.of(enabledConfig));

            ModelConfig result = service.getUserModelConfig("testuser");

            assertNotNull(result);
            assertEquals(1L, result.getId());
        }

        @Test
        @DisplayName("均未配置时返回 null")
        void shouldReturnNullWhenNoConfig() {
            when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            ModelConfig result = service.getUserModelConfig("testuser");

            assertNull(result);
        }
    }

    private ModelName createModelName(Long configId, String name) {
        ModelName mn = new ModelName();
        mn.setModelConfigId(configId);
        mn.setModelName(name);
        mn.setIsAvailable(true);
        return mn;
    }
}
