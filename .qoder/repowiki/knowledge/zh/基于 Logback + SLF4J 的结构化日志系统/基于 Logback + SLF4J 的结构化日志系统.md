---
kind: logging_system
name: 基于 Logback + SLF4J 的结构化日志系统
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/logback-spring.xml
    - src/main/resources/application.yml
    - src/main/java/com/tutorial/offerpilot/agent/middleware/CostControlMiddleware.java
    - src/main/java/com/tutorial/offerpilot/agent/middleware/TokenMonitorMiddleware.java
---

## 1. 使用的系统与框架
- **日志门面**：SLF4J（通过 Lombok `@Slf4j` 注解注入 `log` 实例）
- **日志实现**：Logback（Spring Boot 默认集成），配置文件位于 `src/main/resources/logback-spring.xml`
- **结构化输出**：使用 `net.logstash.logback.encoder.LogstashEncoder`，将文件日志输出为 JSON 格式，便于 ELK/日志平台采集
- **MDC 上下文字段**：配置了 `userId`、`sessionId`、`traceId` 三个 MDC key 自动包含到结构化日志中

## 2. 核心文件与包
- `src/main/resources/logback-spring.xml` — Logback 主配置，定义 Appender、RollingPolicy、Profile 级别开关
- `src/main/resources/application.yml` — Spring 层 logging 属性（file.path、file.name、level、pattern）作为 logback 的后备配置
- `src/main/java/com/tutorial/offerpilot/agent/middleware/CostControlMiddleware.java` — 成本控制的日志埋点示例
- `src/main/java/com/tutorial/offerpilot/agent/middleware/TokenMonitorMiddleware.java` — Token 监控日志示例
- 所有业务类统一通过 `@Slf4j` 注解获取 logger，无自定义 Logger 工厂或 AOP 切面

## 3. 架构与约定
- **双 Appender 策略**：
  - `CONSOLE`：开发环境彩色控制台输出，pattern 含高亮级别、线程名、logger 名
  - `ASYNC_FILE`：异步滚动文件输出，按天+大小（10MB）滚动，保留 7 天，总容量上限 500MB
- **Profile 分级**：
  - `dev/test`：root=INFO，业务包 com.tutorial.offerpilot=DEBUG，Security/Web/SQL 分别控制
  - `prod`：仅文件输出，root=INFO，业务包 INFO，Security=WARN
- **结构化字段**：`app`（应用名）、`env`（active profile）作为 customFields 写入；`userId/sessionId/traceId` 通过 MDC 注入
- **AgentScope Studio 集成**：application.yml 中 `agentscope.studio.enabled=true` 时，Agent 调用链会同时上报至 Studio 进行可视化追踪

## 4. 开发者应遵循的规则
- 使用 Lombok `@Slf4j` 注解声明 logger，禁止自行 `LoggerFactory.getLogger()`
- 关键路径（工具调用、模型请求、错误分支）使用 `log.info`/`log.warn`/`log.error` 记录，参数以 `{}` 占位符形式传入，避免字符串拼接
- 如需跨方法传递上下文（用户 ID、会话 ID、链路追踪 ID），通过 `MDC.put(key, value)` 设置，并在 finally 块中 `MDC.clear()` 清理
- 新增日志级别需同步更新 `logback-spring.xml` 对应 Profile 的 `<logger name="..." level="...">` 配置
- 生产环境避免在日志中输出敏感信息（密码、token、完整请求体），必要时脱敏后再记录