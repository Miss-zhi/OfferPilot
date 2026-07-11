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
    - src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java
    - src/main/java/com/tutorial/offerpilot/config/MilvusConfig.java
    - src/main/java/com/tutorial/offerpilot/config/RedisConfig.java
    - src/main/resources/logback-spring.xml
---

## 系统概述

本项目基于 Spring Boot 3.2，采用 YAML Profile 分层加 @ConfigurationProperties 强类型绑定的配置体系。核心思路是：默认配置集中、环境差异通过 profile 覆盖、敏感信息走环境变量注入。AgentScope v2 的模型、知识库、Embedding、转写等能力均通过独立的子配置块管理，实现 LLM Provider 与 Embedding/Transcription 服务的解耦。

## 关键文件与包

- 配置文件
  - src/main/resources/application.yml — 全局默认配置（数据库、AgentScope、Milvus、Redis、上传目录、日志）
  - src/main/resources/application-dev.yml — 开发环境覆盖（H2 Console、DEBUG 日志）
  - src/main/resources/application-prod.yml — 生产环境覆盖（MySQL、ddl-auto=validate、收紧日志级别）
  - src/main/resources/logback-spring.xml — 基于 springProperty 读取 ACTIVE_PROFILE 动态输出日志路径
- 配置类
  - config/AgentScopeProperties.java — 以 agentscope.* 为前缀的强类型属性绑定，内含 Model/Agent/Knowledge/Embedding/Transcription 五个嵌套子配置
  - config/MilvusConfig.java + config/MilvusProperties.java — Milvus 客户端 Bean 装配
  - config/RedisConfig.java — Redis CacheManager 与 StringRedisTemplate 初始化，按业务 cache 名设置不同 TTL
  - config/SecurityConfig.java / config/WebConfig.java / config/AsyncConfig.java — 安全、Web、异步线程池等基础设施配置

## 架构与约定

1. Profile 分层策略
   - application.yml 提供所有模块的默认值；spring.profiles.active=dev 作为启动入口。
   - application-{profile}.yml 仅声明需要覆盖的差异项，遵循最小覆盖原则。
   - 测试环境通过 AbstractControllerIT / AbstractServiceIT 基类统一加载，未单独放置 application-test.yml，依赖 H2 内存库与内嵌服务。

2. 敏感信息与环境变量注入
   - 所有密钥类配置使用 ${ENV_VAR:default} 语法：DASHSCOPE_API_KEY、EMBEDDING_API_KEY、TRANSCRIPTION_API_KEY、JWT_SECRET、ENCRYPTION_SECRET_KEY、MYSQL_ROOT_PASSWORD。
   - 默认值仅在本地开发时生效，生产必须通过容器或编排平台注入环境变量。

3. AgentScope 配置解耦设计
   - agentscope.model.* 控制主 LLM（DashScope qwen-max），agentscope.embedding.* 和 agentscope.transcription.* 独立配置，支持将 Embedding/转写指向不同 Provider（如 OpenAI），避免共用同一 API Key。
   - agentscope.agent.state-store=redis 指定 Agent 状态持久化后端；agentscope.knowledge.base-path 指向 ./workspace/knowledge，由 DocumentIngestionService 消费。

4. 外部依赖 Bean 装配模式
   - 每个外部中间件（Milvus、Redis、AgentScope）对应一个 *Config 类，负责从 application.yml 读取参数并构造 Client Bean。
   - 连接超时、KeepAlive、TTL 等运行时参数集中在配置文件中，便于按环境调整。

5. 日志配置
   - logback-spring.xml 通过 <springProperty> 读取 spring.profiles.active，结合 logging.level.* 在 dev/prod 间切换详细程度。
   - 控制台与文件输出格式一致，便于统一收集。

## 开发者应遵守的规则

- 新增配置项：优先放入 application.yml 的对应命名空间（app.*、agentscope.*、milvus.*、redis.*），并在对应的 *Properties 或 *Config 中完成类型绑定。
- 敏感字段：一律使用 ${ENV_VAR:} 形式，不得硬编码默认值进入生产分支。
- 环境差异：只在新建 application-{profile}.yml 中声明覆盖项，禁止修改 application.yml 中的默认值来适配特定环境。
- AgentScope 扩展：如需新增独立服务（如新的向量检索后端），参照 MilvusConfig 模式创建 XxxConfig + XxxProperties，并在 application.yml 中以 app.xxx.* 暴露配置。
- 测试配置：集成测试通过继承 AbstractServiceIT / AbstractControllerIT 复用默认 H2 配置，无需额外 profile 文件。