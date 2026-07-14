---
kind: configuration_system
name: Spring Boot 多环境配置与 AgentScope 属性体系
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
    - src/main/resources/application-prod.yml
    - src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java
    - src/main/java/com/tutorial/offerpilot/config/MilvusProperties.java
    - src/main/java/com/tutorial/offerpilot/config/RedisConfig.java
    - src/main/java/com/tutorial/offerpilot/config/StudioIntegrationConfig.java
    - src/main/java/com/tutorial/offerpilot/config/AsyncConfig.java
---

## 系统概述

本项目采用 Spring Boot 原生配置体系，结合 `@ConfigurationProperties` 类型安全绑定，形成「YAML 分层 + 环境变量注入 + 独立 Properties 类」的三层配置架构。AgentScope AI 框架的配置通过统一的 `agentscope.*` 命名空间集中管理，并支持 Embedding、Transcription、Rerank、Studio 等子模块的独立开关与回退策略。

## 配置文件与加载顺序

- **主配置**：`src/main/resources/application.yml` — 定义默认值、profile 激活（dev）、`spring.config.import: optional:file:.env[.properties]` 引入本地环境变量文件。
- **环境覆盖**：`application-dev.yml` / `application-prod.yml` — 按 profile 覆盖数据源、日志级别、JPA ddl-auto 等差异项。
- **环境变量**：通过 `${VAR:default}` 语法在 YAML 中注入，如 `MYSQL_ROOT_PASSWORD`、`DASHSCOPE_API_KEY`、`JWT_SECRET`、`ENCRYPTION_SECRET_KEY`、`EMBEDDING_API_KEY`、`TRANSCRIPTION_API_KEY`、`RERANK_API_KEY`。

## 核心配置类与命名空间

| 前缀 | 作用 | 关键类 |
|------|------|--------|
| `agentscope.*` | AgentScope 全量配置（LLM/Embedding/Transcription/Rerank/Studio） | `AgentScopeProperties` |
| `app.milvus.*` | Milvus 向量数据库连接 | `MilvusProperties` |
| `app.async.*` | 异步线程池参数 | 直接 `@Value` 注入到 `AsyncConfig` |
| `app.jwt.*` / `app.encryption.*` | JWT 与密钥 | 各组件 `@Value` 注入 |
| `app.upload-dir` / `app.allowed-file-types` | 文件上传目录与白名单 | `FileService` |

## 设计约定与规则

1. **类型安全优先**：复杂配置使用 `@ConfigurationProperties(prefix = "...")` + Lombok `@Data` 生成 Bean，禁止散落的 `@Value("${...}")` 字符串拼接。
2. **敏感信息走环境变量**：所有 API Key、密码、JWT Secret 必须通过 `${ENV_VAR:default}` 注入，不在仓库中硬编码。
3. **模块化解耦**：Embedding、Transcription、Rerank 各自拥有独立 `apiKey`/`baseUrl`，未显式配置时自动回退到 `agentscope.model.api-key`，实现 LLM Provider 切换不影响其他能力。
4. **Feature Flag 模式**：`agentscope.studio.enabled`、`agentscope.rerank.enabled` 等布尔开关控制可选功能，启动失败不阻断应用（`StudioIntegrationConfig` 捕获异常仅记录日志）。
5. **Profile 隔离**：dev 开启 DEBUG 日志与 H2 Console，prod 关闭调试、启用 `ddl-auto: validate`，部署脚本需指定 `--spring.profiles.active=prod`。
6. **Bean 初始化生命周期**：Studio 集成通过 `@PostConstruct`/`@PreDestroy` 管理连接生命周期，确保优雅启停。

## 关键文件清单

- `src/main/resources/application.yml` — 全局默认配置与环境变量映射
- `src/main/resources/application-dev.yml` / `application-prod.yml` — 环境差异化覆盖
- `src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java` — AgentScope 配置聚合类
- `src/main/java/com/tutorial/offerpilot/config/MilvusProperties.java` — Milvus 连接属性
- `src/main/java/com/tutorial/offerpilot/config/RedisConfig.java` — Redis 缓存管理器与 TTL 策略
- `src/main/java/com/tutorial/offerpilot/config/StudioIntegrationConfig.java` — Studio 监控集成开关
- `src/main/java/com/tutorial/offerpilot/config/AsyncConfig.java` — 异步线程池参数注入
- `src/main/java/com/tutorial/offerpilot/service/ApiKeyEncryption.java` — 动态解密密钥注入

## 开发者注意事项

- 新增外部服务配置时，优先创建独立的 `*Properties` 类并使用 `@ConfigurationProperties`，而非直接 `@Value`。
- 新增环境变量后，同步更新 `application.yml` 中的 `${VAR:default}` 占位符，保证本地可运行。
- 对可选依赖（如 Studio、Rerank）提供 `enabled=false` 默认值，避免生产环境因第三方不可用导致启动失败。
