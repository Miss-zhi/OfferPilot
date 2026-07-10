---
kind: configuration_system
name: Spring Boot 多环境配置与外部化参数体系
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
    - src/main/resources/application-prod.yml
    - src/test/resources/application-test.yml
    - .env
    - docker-compose.yml
    - src/main/java/com/tutorial/offerpilot/config/AgentScopeProperties.java
    - src/main/java/com/tutorial/offerpilot/config/MilvusProperties.java
    - src/main/java/com/tutorial/offerpilot/config/MilvusConfig.java
    - src/main/java/com/tutorial/offerpilot/config/RedisConfig.java
---

## 1. 系统概览
OfferPilot 采用 Spring Boot 原生 application.yml + @ConfigurationProperties + 环境变量注入的三层配置模型，通过 spring.profiles.active 在 dev/prod/test 之间切换，敏感值统一由 .env 文件经 Docker Compose 注入到容器或 JVM 进程。

## 2. 核心文件与包
- 配置文件
  - src/main/resources/application.yml：全局默认配置（H2、端口、日志、AgentScope、Milvus、Redis、异步线程池等）
  - src/main/resources/application-dev.yml：开发环境覆盖（开启 H2 Console、DEBUG 日志）
  - src/main/resources/application-prod.yml：生产环境覆盖（MySQL、ddl-auto=validate、收紧日志级别）
  - src/test/resources/application-test.yml：集成测试覆盖（Testcontainers MySQL、内存状态存储、禁用 Milvus/Redis 自动初始化）
  - .env：本地开发密钥与数据库密码模板
  - docker-compose.yml：编排 MySQL/Redis/Milvus/etcd/MinIO，并通过 ${VAR:-default} 将 .env 变量注入容器
- Java 配置类（com.tutorial.offerpilot.config）
  - AgentScopeProperties.java：以 agentscope.* 前缀绑定 AgentScope 模型、Agent、Knowledge 子配置
  - MilvusProperties.java：以 app.milvus.* 前缀绑定向量库连接参数
  - MilvusConfig.java：基于 MilvusProperties 构造 MilvusClientV2 Bean
  - RedisConfig.java：暴露 StringRedisTemplate Bean
  - AsyncConfig.java / WebConfig.java / SecurityConfig.java：其他 Spring 扩展点

## 3. 架构与约定
- Profile 分层：application.yml 提供可运行默认值；dev/prod/test profile 仅做增量覆盖，避免重复定义。
- 类型安全绑定：业务相关配置全部通过 @ConfigurationProperties(prefix = "...") 声明为 POJO（如 AgentScopeProperties、MilvusProperties），禁止在代码中硬编码字符串 key。
- 环境变量占位符：所有敏感信息使用 ${ENV_VAR:默认值} 语法，例如 DASHSCOPE_API_KEY、JWT_SECRET、MYSQL_ROOT_PASSWORD，默认值仅在本地开发时生效。
- 基础设施即配置：docker-compose.yml 集中声明 MySQL/Redis/Milvus/etcd/MinIO 镜像、端口、健康检查与卷挂载，应用侧只关心 localhost 地址。
- 测试隔离：application-test.yml 关闭 H2 Console、禁用 AgentScope auto-init、将 workspace/upload-dir 指向 target/ 下临时目录，配合 Testcontainers 动态注入 JDBC URL。

## 4. 开发者应遵循的规则
1. 新增配置项：优先在 application.yml 中给出合理默认值，并在对应 profile 文件中按需覆盖；敏感字段一律使用 ${VAR:默认值} 形式。
2. 类型安全：对超过 3 个字段的配置组，新建 @ConfigurationProperties 类并注册为 @Component，在 Service 中直接注入该 Properties 对象。
3. 不要硬编码：禁止在 Java 源码中出现明文密码、API Key、host/port 等运行时可变参数。
4. Profile 最小化：每个 profile 只写差异部分，保持 application.yml 作为单一事实来源。
5. 测试配置独立：集成测试必须通过 application-test.yml 或 @DynamicPropertySource 覆盖数据源、缓存、向量库等外部依赖，不得复用 prod 配置。
6. 环境变量命名：.env 中的变量名应与 application*.yml 中 ${...} 占位符一一对应，并在 docker-compose.yml 中以相同名称透传。