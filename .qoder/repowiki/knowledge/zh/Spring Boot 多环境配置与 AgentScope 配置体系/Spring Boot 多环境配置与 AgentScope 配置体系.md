---
kind: configuration_system
name: Spring Boot 多环境配置与 AgentScope 配置体系
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
    - src/main/resources/application-prod.yml
    - src/test/resources/application-test.yml
    - src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java
    - src/main/java/com/tutorial/offerpilot/config/MilvusProperties.java
    - src/main/java/com/tutorial/offerpilot/config/RedisConfig.java
    - src/main/java/com/tutorial/offerpilot/config/MilvusConfig.java
    - src/main/java/com/tutorial/offerpilot/config/StudioIntegrationConfig.java
---

## 系统概述

OfferPilot 采用 Spring Boot 3.2 原生配置体系，结合自定义 `@ConfigurationProperties` 类管理应用级配置。配置文件按环境分层，通过 Profile 机制切换；敏感信息通过环境变量注入；AgentScope AI 框架的配置被拆分为独立子模块，支持 LLM、Embedding、转写等能力解耦。

## 核心架构与约定

### 1. 配置文件分层结构

- **基础配置**：`src/main/resources/application.yml` — 所有环境共享的默认值
- **开发环境**：`application-dev.yml` — 覆盖开发相关设置（如连接池大小、日志级别）
- **生产环境**：`application-prod.yml` — 覆盖生产相关设置（如数据库 URL、JPA 校验模式）
- **测试环境**：`src/test/resources/application-test.yml` — 测试专用配置，禁用 AgentScope 自动初始化，使用内存状态存储

Profile 激活顺序：`application.yml` → `application-{profile}.yml`，后者覆盖前者同名属性。

### 2. 环境变量注入策略

项目广泛使用 `${ENV_VAR:default}` 语法注入环境变量，关键敏感字段包括：
- `DASHSCOPE_API_KEY` / `EMBEDDING_API_KEY` / `TRANSCRIPTION_API_KEY` — 各服务 API Key
- `MYSQL_USERNAME` / `MYSQL_ROOT_PASSWORD` — 数据库凭据
- `JWT_SECRET` / `ENCRYPTION_SECRET_KEY` — 安全密钥

此外通过 `spring.config.import: optional:file:.env[.properties]` 支持从 `.env` 文件加载本地开发变量。

### 3. 类型安全的配置绑定

通过 `@ConfigurationProperties(prefix = "...")` + `@Component` 将 YAML 节点映射为 Java 对象：

- **AgentScopeProperties** (`agentscope.*`) — 统一管理 LLM、Agent、知识库、Embedding、转写、Studio 五大子配置块
- **MilvusProperties** (`app.milvus.*`) — 向量数据库连接参数
- 其他配置直接通过 `@Value("${...}")` 或 `Environment` 注入

每个配置类均提供合理的默认值，确保未显式配置时仍能运行。

### 4. 外部依赖配置

- **Redis**：`RedisConfig` 定义缓存管理器，为不同业务域（searchQuestions、searchAnswers 等）配置独立 TTL
- **Milvus**：`MilvusConfig` 基于 `MilvusProperties` 构建客户端连接
- **异步线程池**：`AsyncConfig` 暴露 core/max/queue 参数供业务组件使用
- **Web 与安全**：`WebConfig`、`SecurityConfig` 处理跨域、拦截器等横切关注点

### 5. AgentScope 配置设计要点

- **LLM 与 Embedding 解耦**：`agentscope.embedding.api-key` 可独立于 `agentscope.model.api-key`，便于切换不同服务商
- **转写服务独立配置**：`agentscope.transcription` 支持 OpenAI 兼容端点，Key 优先级：`TRANSCRIPTION_API_KEY` > `DASHSCOPE_API_KEY` > model.api-key
- **Studio 集成开关**：`agentscope.studio.enabled=false` 默认关闭，避免生产环境开销；启用后在 `@PostConstruct` 阶段建立 WebSocket 连接

## 开发者规范

1. **新增配置项**：优先创建独立的 `*Properties` 类并使用 `@ConfigurationProperties` 绑定，而非散落的 `@Value`
2. **敏感信息**：一律通过环境变量注入，禁止硬编码到配置文件
3. **默认值**：所有配置类必须提供合理默认值，保证最小化配置即可启动
4. **环境差异**：仅将与环境强相关的配置放入 `application-{profile}.yml`，通用逻辑留在主配置
5. **测试隔离**：测试配置需禁用外部依赖自动初始化（如 `agentscope.knowledge.auto-init=false`），使用内存替代方案
6. **配置验证**：对必填字段应在 `@PostConstruct` 或 Bean 初始化时进行校验并抛出明确异常

## 关键文件清单

- `src/main/resources/application.yml` — 全局默认配置
- `src/main/resources/application-dev.yml` — 开发环境覆盖
- `src/main/resources/application-prod.yml` — 生产环境覆盖
- `src/test/resources/application-test.yml` — 测试环境覆盖
- `src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java` — AgentScope 全量配置绑定
- `src/main/java/com/tutorial/offerpilot/config/MilvusProperties.java` — Milvus 配置绑定
- `src/main/java/com/tutorial/offerpilot/config/RedisConfig.java` — Redis 缓存与连接配置
- `src/main/java/com/tutorial/offerpilot/config/MilvusConfig.java` — Milvus 客户端 Bean 定义
- `src/main/java/com/tutorial/offerpilot/config/StudioIntegrationConfig.java` — Studio 监控集成生命周期管理
- `src/main/java/com/tutorial/offerpilot/config/AsyncConfig.java` — 异步线程池配置
- `src/main/java/com/tutorial/offerpilot/config/SecurityConfig.java` — 安全与 Web 配置
- `src/main/java/com/tutorial/offerpilot/config/WebConfig.java` — Web MVC 扩展配置
