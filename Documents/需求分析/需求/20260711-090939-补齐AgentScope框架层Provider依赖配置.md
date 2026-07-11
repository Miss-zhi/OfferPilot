# Copyright (c) 2020-06-29 Qoder. All rights reserved.

# 多 Provider 模型解析适配 — 补齐 AgentScope 框架层依赖配置

## 1. 背景与目标

### 背景

OfferPilot 通过 [ProviderPreset](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/enums/ProviderPreset.java) 枚举预设了 8 家主流 LLM Provider（DashScope、OpenAI、DeepSeek、SiliconFlow、VolcEngine、Anthropic、Gemini、Ollama），并通过 [AgentFactory](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java) 的 `resolveModel()` 方法动态构建 `provider:modelName` 格式的 modelId，调用 `ModelRegistry.resolve()` 创建 Model 实例。

AgentScope Java v2 框架采用 **Java ServiceLoader SPI** 机制加载 `ModelProvider` 实现——每个 Provider 对应一个独立的 Maven 模块（extension JAR）。`agentscope-core` 自身不包含任何 `ModelProvider` 实现。

经审查发现：项目从 v5.0 初始版本起，[pom.xml](file:///opt/OfferPilot/pom.xml) 中仅引入了 `agentscope-extensions-model-openai`（且是在 v5.0 之后的提交才引入的），其余 4 个 extension JAR 全部缺失；此外 DeepSeek、SiliconFlow、VolcEngine 这三个 OpenAI 兼容协议 Provider 在 AgentScope 中并无独立 SPI Provider，必须复用 `openai:` 前缀。

**后果**：8 个预设 Provider 中有 7 个在运行时无法通过 `ModelRegistry` 解析，直接抛出 `IllegalArgumentException: Cannot resolve model`。

### 目标

- 补齐所有缺失的 AgentScope Model Extension 依赖（dashscope / anthropic / gemini / ollama）
- 修复 AgentFactory 中 OpenAI 兼容 Provider 的 modelId 映射逻辑（deepseek / siliconflow / volcengine → openai 前缀 + 自定义 baseUrl）
- 修复 application.yml 兜底配置，确保启动即可用
- 确保 admin 通过界面配置任意 Provider + API Key 后，Agent 可正常调用对应 LLM 服务

### 不做什么

- 不新增 Provider 预设（当前 8 个已足够）
- 不修改数据库表结构
- 不修改前端代码
- 不修改 API 接口

---

## 2. 功能清单

| 功能模块 | 功能点 | 优先级 | 说明 |
|---------|--------|--------|------|
| Maven 依赖 | 补充 dashscope/anthropic/gemini/ollama 四个 extension 依赖 | P0 | 无此 JAR，ModelRegistry 无法解析对应 modelId |
| AgentFactory | 增加 OpenAI 兼容 Provider 的 modelId 前缀映射 | P0 | deepseek/siliconflow/volcengine 无独立 SPI Provider |
| application.yml | 兜底 provider + model-name 改为可被解析的值 | P0 | 当前 `deepseek:deepseek-chat` 同样无法解析 |
| ProviderPreset | 增加 `agentScopeProvider` 映射字段 | P1 | 提升语义明确性，避免硬编码映射 |
| 启动自检 | AgentFactory 启动时打印已加载的 Provider 清单 | P2 | 运维排查友好 |

---

## 3. 涉及模块

### 后端

| 模块 | 文件 | 变更内容 |
|------|------|---------|
| Maven 构建 | [pom.xml](file:///opt/OfferPilot/pom.xml) | 新增 4 个 `<dependency>` |
| Agent 工厂 | [AgentFactory.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java) | `resolveModel()` 增加 Provider 映射逻辑 |
| 配置 | [application.yml](file:///opt/OfferPilot/src/main/resources/application.yml) | 修改 `agentscope.model.provider` 和 `agentscope.model.model-name` |
| 枚举 | [ProviderPreset.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/enums/ProviderPreset.java) | 可选：增加 `agentScopeProvider` 字段 |

### 前端

无变更。

### 数据库

无变更。

---

## 4. 技术方案

### 4.1 Provider → AgentScope ModelProvider 映射关系

AgentScope v2 框架通过 SPI 提供 5 个 ModelProvider：

| AgentScope ModelProvider | Module | 匹配模式 | 适用 OfferPilot ProviderPreset |
|---|---|---|---|
| `openai` | agentscope-extensions-model-openai | `openai:.+` | openai, **deepseek, siliconflow, volcengine** |
| `dashscope` | agentscope-extensions-model-dashscope | `dashscope:.+` / `qwen.+` | dashscope |
| `anthropic` | agentscope-extensions-model-anthropic | `anthropic:.+` | anthropic |
| `gemini` | agentscope-extensions-model-gemini | `gemini:.+` | gemini |
| `ollama` | agentscope-extensions-model-ollama | `ollama:.+` | ollama |

**关键**：DeepSeek / SiliconFlow / VolcEngine 使用 OpenAI 兼容 API，modelId 前缀必须映射为 `openai:`，同时通过 `ModelCreationContext.baseUrl` 传入各自的自定义 Base URL。

### 4.2 pom.xml 变更

当前仅引入 `agentscope-extensions-model-openai`，需补充：

```xml
<!-- DashScope -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<!-- Anthropic -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-anthropic</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<!-- Gemini -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-gemini</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<!-- Ollama -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-ollama</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

### 4.3 AgentFactory.resolveModel() 映射逻辑

在 `resolveModel()` 方法中，调用 `ModelRegistry.resolve()` 之前增加 Provider 映射：

```java
/**
 * OpenAI 兼容但非 openai 前缀的 Provider → 统一映射为 openai: 前缀。
 * 这些 Provider 在 AgentScope 中无独立 SPI Provider，需复用 OpenAIModelProvider。
 */
private static final Set<String> OPENAI_COMPATIBLE_PROVIDERS = 
        Set.of("deepseek", "siliconflow", "volcengine");

/**
 * ProviderPreset.providerKey → AgentScope SPI providerId 映射。
 */
private String mapToAgentScopeProvider(String provider) {
    if (OPENAI_COMPATIBLE_PROVIDERS.contains(provider)) {
        return "openai";
    }
    return provider; // dashscope / openai / anthropic / gemini / ollama
}
```

完整流程：

```
admin 配置 provider=dashscope, modelName=qwen-max
  → AgentScope providerId = "dashscope"
  → modelId = "dashscope:qwen-max"
  → ModelRegistry.resolve("dashscope:qwen-max", ctx)
  → 匹配 DashScopeModelProvider ✅

admin 配置 provider=deepseek, modelName=deepseek-chat, baseUrl=https://api.deepseek.com
  → AgentScope providerId = "openai"
  → modelId = "openai:deepseek-chat"
  → ModelRegistry.resolve("openai:deepseek-chat", ctx{baseUrl="https://api.deepseek.com"})
  → 匹配 OpenAIModelProvider，baseUrl 覆盖默认 OpenAI URL ✅
```

### 4.4 application.yml 兜底修复

```yaml
# 修改前（不可用）
agentscope:
  model:
    provider: deepseek
    model-name: deepseek-chat

# 修改后（推荐：与 AgentScope 原生 Provider 对齐）
agentscope:
  model:
    provider: dashscope
    model-name: qwen-max
```

---

## 5. 数据库变更

无。

---

## 6. 接口设计

无新增或修改的 API 接口。现有接口不变：

- `GET /api/v1/admin/models/provider-presets` — 返回 8 个预设（不变）
- `POST /api/v1/admin/models` — 创建模型配置（不变）
- `GET /api/v1/user/models` — 用户可用模型（不变）
- 聊天接口 — 内部 AgentFactory 逻辑修复后自动生效

---

## 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| [pom.xml](file:///opt/OfferPilot/pom.xml) | 修改 | 新增 4 个 Model Extension 依赖 |
| [AgentFactory.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java) | 修改 | `resolveModel()` 增加 Provider 映射 + 启动自检日志 |
| [application.yml](file:///opt/OfferPilot/src/main/resources/application.yml) | 修改 | 兜底 provider 改为 `dashscope` |
| [ProviderPreset.java](file:///opt/OfferPilot/src/main/java/com/tutorial/offerpilot/enums/ProviderPreset.java) | 可选修改 | 增加 `agentScopeProvider` 字段提升语义性 |

---

## 验收标准

1. `mvn clean compile` 通过，依赖下载成功
2. 启动应用后日志显示 "Loaded AgentScope ModelProviders: [openai, dashscope, anthropic, gemini, ollama]"
3. admin 配置 `dashscope` + API Key + `qwen-max` → 对话正常调用 DashScope
4. admin 配置 `deepseek` + API Key + `deepseek-chat` → 对话正常调用 DeepSeek（通过 OpenAI 适配）
5. admin 配置 `anthropic` + API Key + `claude-sonnet-4-20250514` → 对话正常调用 Anthropic
6. admin 配置 `gemini` + API Key + `gemini-2.0-flash` → 对话正常调用 Gemini
7. admin 配置 `ollama` → 对话正常调用本地 Ollama
8. admin 配置 `siliconflow` → 对话正常调用硅基流动
9. admin 配置 `volcengine` → 对话正常调用火山引擎
