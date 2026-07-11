---
kind: configuration_system
name: Spring Boot 多环境配置与 AgentScope 属性绑定体系
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
    - src/main/resources/application-prod.yml
    - src/test/resources/application-test.yml
    - src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java
    - src/main/java/com/tutorial/offerpilot/config/MilvusConfig.java
    - src/main/java/com/tutorial/offerpilot/config/RedisConfig.java
    - src/main/java/com/tutorial/offerpilot/config/AsyncConfig.java
---

## 1. 系统概览

本项目基于 Spring Boot 3.2，采用 **YAML Profile + `@ConfigurationProperties` 强类型绑定** 的官方推荐方式管理配置。核心思路：

- 通过 `application.yml` 提供默认值（H2 + Dev）；
- 通过 `application-{profile}.yml` 覆盖特定环境差异（dev/prod/test）；
- 通过 `agentscope.*`、`app.*` 等自定义前缀将外部依赖（AgentScope、Milvus、Redis、上传目录等）以强类型 POJO 注入到业务代码；
- 敏感信息一律通过环境变量 `${VAR:default}` 注入，避免硬编码。

## 2. 关键文件与包

| 类别 | 路径 | 作用 |
|---|---|---|
| 主配置 | `src/main/resources/application.yml` | 全局默认配置（端口、Datasource、AgentScope、App、日志） |
| 开发环境 | `src/main/resources/application-dev.yml` | H2 Console 开启、DEBUG 日志 |
| 生产环境 | `src/main/resources/application-prod.yml` | MySQL Datasource、JPA validate、日志降为 INFO/WARN |
| 测试环境 | `src/test/resources/application-test.yml` | Testcontainers MySQL、内存状态存储、禁用自动初始化 |
| AgentScope 属性 | `config/AgentScopeProperties.java` | `agentscope.*` 强类型绑定（Model/Agent/Knowledge/Embedding/Transcription） |
| Milvus 客户端 | `config/MilvusConfig.java` + `config/MilvusProperties.java` | 从 `app.milvus.*` 构建 `MilvusClientV2` Bean |
| Redis 缓存 | `config/RedisConfig.java` | `StringRedisTemplate` + 按 Cache 名分 TTL 的 `RedisCacheManager` |
| 异步线程池 | `config/AsyncConfig.java` | 基于 `app.async.*` 的 `TaskExecutor` Bean |
| Web/安全 | `config/WebConfig.java`、`config/SecurityConfig.java` | CORS、拦截器、JWT 过滤器等 |

## 3. 架构与约定

### 3.1 Profile 分层策略

```
application.yml (默认)
├── application-dev.yml   ← spring.profiles.active=dev (本地开发)
├── application-prod.yml  ← 容器/Docker 部署时激活
└── application-test.yml  ← @ActiveProfiles("test") 集成测试使用
```

- 默认 profile 指向 `dev`，本地启动即获得 H2 + DEBUG 日志；
- 生产通过 `-Dspring.profiles.active=prod` 或环境变量切换；
- 测试基类 `AbstractControllerIT` / `AbstractServiceIT` 统一声明 `@ActiveProfiles("test")`。

### 3.2 强类型属性绑定

所有外部依赖的配置均通过 `@ConfigurationProperties(prefix = "...")` 暴露为嵌套静态类：

- `agentscope.model.*` — LLM Provider、API Key、Base URL、模型名、温度、最大 Token；
- `agentscope.agent.*` — workspace 路径、state-store 后端（redis/memory）、compaction 开关；
- `agentscope.knowledge.*` — 知识库根路径、embedding 模型、chunk 大小/重叠、topK、是否 auto-init；
- `agentscope.embedding.*` — 独立于 LLM 的 Embedding Provider（支持 DeepSeek 等无 Embedding 能力场景）；
- `agentscope.transcription.*` — 录音转写专用端点与 Key（回退至 model.api-key）；
- `app.jwt.*`、`app.encryption.*`、`app.milvus.*`、`app.redis.*`、`app.upload-dir`、`app.allowed-file-types`、`app.async.*`。

这种设计使配置变更无需修改 Java 源码，且 IDE 具备完整提示。

### 3.3 环境变量优先级与回退链

- 所有密钥类配置均采用 `${ENV_VAR:default}` 语法，例如：
  - `agentscope.model.api-key: ${DASHSCOPE_API_KEY:sk-xxx}`
  - `agentscope.embedding.api-key: ${EMBEDDING_API_KEY:${DASHSCOPE_API_KEY:}}`
  - `agentscope.transcription.api-key: ${TRANSCRIPTION_API_KEY:${DASHSCOPE_API_KEY:}}`
  - `app.jwt.secret: ${JWT_SECRET:dev-jwt-secret-change-in-production}`
- 回退链语义清晰：**显式环境变量 > 配置文件默认值 > 代码中硬编码默认值**。

### 3.4 外部服务 Bean 装配

- `MilvusConfig` 读取 `app.milvus.*` 构造 `io.milvus.v2.client.MilvusClientV2`；
- `RedisConfig` 启用 `@EnableCaching`，并针对 `searchQuestions` / `searchAnswers` / `searchCompanyInterviews` / `searchResources` 四个 Cache 分别设置 5 分钟 TTL；
- `AsyncConfig` 基于 `app.async.*` 创建线程池 Bean，供异步任务使用。

## 4. 开发者应遵循的规则

1. **新增配置项必须走 `@ConfigurationProperties`**
   在对应 Properties 类中添加字段，并在 YAML 中给出默认值，禁止在业务代码中直接 `@Value("${...}")` 散落字符串。

2. **敏感信息一律通过环境变量注入**
   不要在 YAML 中明文写入 API Key、JWT Secret、数据库密码；使用 `${VAR:default}` 形式，并在部署文档中说明所需环境变量。

3. **Profile 只覆盖差异，不重复全量配置**
   `application-dev.yml` / `application-prod.yml` 仅包含与默认值不同的片段，保持单一事实源。

4. **测试环境隔离**
   集成测试统一使用 `application-test.yml`，并通过 `@DynamicPropertySource` 注入 Testcontainers 动态端口，禁止在测试中连接真实外部服务。

5. **新增外部依赖时同步更新三处**
   - `application.yml` 中的默认值；
   - `AgentScopeProperties` / 对应 Properties 类的字段与注释；
   - `application-prod.yml` / `application-test.yml` 中的覆盖值。

6. **日志级别按环境区分**
   dev 使用 DEBUG，prod 使用 INFO/WARN，测试使用 WARN，避免在生产泄露调试信息。