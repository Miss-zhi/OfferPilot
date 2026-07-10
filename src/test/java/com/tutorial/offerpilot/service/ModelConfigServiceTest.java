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
@DisplayName("ModelConfigService 单元测试")
class ModelConfigServiceTest {

    @Mock private ModelConfigRepository configRepo;
    @Mock private ModelNameRepository nameRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private ApiKeyEncryption encryption;
    @Mock private ModelListFetcher fetcher;

    @InjectMocks
    private ModelConfigService service;

    @BeforeEach
    void setUp() {
        lenient().when(encryption.encrypt(anyString())).thenAnswer(inv -> "encrypted:" + inv.getArgument(0));
        lenient().when(encryption.decrypt(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.startsWith("encrypted:") ? s.substring(10) : "decrypted";
        });
    }

    @Nested
    @DisplayName("listConfigs")
    class ListConfigsTests {

        @Test
        @DisplayName("返回非私有模型配置列表")
        void shouldReturnNonPrivateConfigs() {
            ModelConfig config = new ModelConfig();
            config.setId(1L);
            config.setProvider("dashscope");
            config.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
            config.setApiFormat("openai");
            config.setAuthHeaderType("bearer");
            config.setModelListUrl("https://models.example.com");
            config.setIsEnabled(true);
            config.setIsGlobalDefault(false);
            config.setIsPrivate(false);
            config.setApiKey("encrypted:sk-test");

            when(configRepo.findByIsPrivateFalse()).thenReturn(List.of(config));
            when(nameRepo.findByModelConfigId(1L)).thenReturn(List.of());

            List<ModelConfigResponse> result = service.listConfigs();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("dashscope", result.get(0).getProvider());
            verify(configRepo).findByIsPrivateFalse();
        }
    }

    @Nested
    @DisplayName("createConfig")
    class CreateConfigTests {

        @Test
        @DisplayName("新增配置并拉取模型名称")
        void shouldCreateConfigAndFetchModels() {
            CreateModelConfigRequest req = new CreateModelConfigRequest();
            req.setProvider("dashscope");
            req.setApiKey("sk-test");

            when(configRepo.save(any(ModelConfig.class))).thenAnswer(inv -> {
                ModelConfig c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            when(fetcher.fetchModelNames(eq("openai"), anyString(), eq("sk-test"), eq("bearer")))
                    .thenReturn(List.of("qwen-max", "qwen-plus"));

            ModelConfigResponse result = service.createConfig(req);

            assertNotNull(result);
            assertEquals("dashscope", result.getProvider());
            verify(configRepo).save(any(ModelConfig.class));
            verify(fetcher).fetchModelNames(eq("openai"), anyString(), eq("sk-test"), eq("bearer"));
        }

        @Test
        @DisplayName("未知 Provider 抛 BusinessException")
        void shouldThrowForUnknownProvider() {
            CreateModelConfigRequest req = new CreateModelConfigRequest();
            req.setProvider("unknown");
            req.setApiKey("sk-test");

            assertThrows(BusinessException.class, () -> service.createConfig(req));
        }
    }

    @Nested
    @DisplayName("deleteConfig")
    class DeleteConfigTests {

        @Test
        @DisplayName("无用户引用时删除成功")
        void shouldDeleteWhenNoUsers() {
            ModelConfig config = new ModelConfig();
            config.setId(1L);

            when(configRepo.findById(1L)).thenReturn(Optional.of(config));
            when(userRepo.countByDefaultModelConfigIdOrPrivateModelConfigId(1L, 1L)).thenReturn(0L);

            assertDoesNotThrow(() -> service.deleteConfig(1L));
            verify(nameRepo).deleteByModelConfigId(1L);
            verify(configRepo).delete(config);
        }

        @Test
        @DisplayName("有用户引用时抛异常")
        void shouldThrowWhenHasUsers() {
            ModelConfig config = new ModelConfig();
            config.setId(1L);

            when(configRepo.findById(1L)).thenReturn(Optional.of(config));
            when(userRepo.countByDefaultModelConfigIdOrPrivateModelConfigId(1L, 1L)).thenReturn(3L);

            assertThrows(BusinessException.class, () -> service.deleteConfig(1L));
            verify(configRepo, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("setGlobalDefault")
    class SetGlobalDefaultTests {

        @Test
        @DisplayName("设置全局默认成功")
        void shouldSetGlobalDefault() {
            ModelConfig config = new ModelConfig();
            config.setId(2L);
            config.setIsEnabled(true);
            config.setApiKey("encrypted:sk-test");

            ModelConfig oldDefault = new ModelConfig();
            oldDefault.setId(1L);
            oldDefault.setIsGlobalDefault(true);

            when(configRepo.findById(2L)).thenReturn(Optional.of(config));
            when(configRepo.findByIsGlobalDefaultTrue()).thenReturn(Optional.of(oldDefault));
            when(nameRepo.findByModelConfigIdAndIsAvailableTrue(2L))
                    .thenReturn(List.of(createModelName(2L, "qwen-max")));
            when(configRepo.save(any(ModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

            ModelConfigResponse result = service.setGlobalDefault(2L, "qwen-max");

            assertNotNull(result);
            assertTrue(config.getIsGlobalDefault());
            assertFalse(oldDefault.getIsGlobalDefault());
            verify(configRepo, times(2)).save(any(ModelConfig.class));
        }

        @Test
        @DisplayName("禁用的模型不能设为全局默认")
        void shouldNotSetDisabledAsGlobalDefault() {
            ModelConfig config = new ModelConfig();
            config.setId(2L);
            config.setIsEnabled(false);

            when(configRepo.findById(2L)).thenReturn(Optional.of(config));

            assertThrows(BusinessException.class, () -> service.setGlobalDefault(2L, "qwen-max"));
        }
    }

    @Nested
    @DisplayName("listProviderPresets")
    class ListProviderPresetsTests {

        @Test
        @DisplayName("返回所有 8 个 Provider 预设")
        void shouldReturnAllPresets() {
            List<ProviderPresetResponse> result = service.listProviderPresets();

            assertEquals(8, result.size());
            assertTrue(result.stream().anyMatch(p -> p.getProvider().equals("dashscope")));
            assertTrue(result.stream().anyMatch(p -> p.getProvider().equals("openai")));
            assertTrue(result.stream().anyMatch(p -> p.getProvider().equals("anthropic")));
            assertTrue(result.stream().anyMatch(p -> p.getProvider().equals("gemini")));
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
