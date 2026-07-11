---
kind: logging_system
name: 基于 Logback + LogstashEncoder 的结构化日志系统
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/logback-spring.xml
    - src/main/resources/application.yml
---

## 1. 使用的系统与框架
- 日志实现：Logback（Spring Boot 默认）
- 结构化输出：`net.logstash.logback.encoder.LogstashEncoder`，将文件日志输出为 JSON（Logstash/ELK 友好格式）
- 控制台与文件双 Appender，生产环境仅落盘
- 异步写入：通过 `AsyncAppender` 包装 RollingFileAppender，队列 512、永不丢弃、不阻塞
- 日志级别按 Profile 区分：dev/test 对业务包 DEBUG，prod INFO

## 2. 核心配置文件
- `src/main/resources/logback-spring.xml`：定义 CONSOLE / FILE / ASYNC_FILE Appender、滚动策略（按天+大小 10MB、保留 7 天、总上限 500MB）、MDC 字段注入、Profile 级别控制
- `src/main/resources/application.yml`：提供 `logging.file.path`、`logging.file.name`、`logging.level.*`、`logging.pattern.*` 等运行时覆盖项；同时声明 `io.agentscope`、`org.springframework.security`、`org.hibernate.SQL` 等第三方包的级别
- 应用名 `spring.application.name=offerpilot` 作为 `APP_NAME` 注入到 JSON 的 `customFields.app`

## 3. 架构与约定
- **统一入口**：所有类使用 Lombok `@Slf4j` 获取 logger，无自定义 Logger 工厂或门面
- **结构化字段**：通过 `includeMdcKeyName` 在 JSON 中自动包含 `userId`、`sessionId`、`traceId` 三个 MDC 键，便于跨服务链路追踪。当前代码中这些键尚未显式设置，属于预留扩展点
- **日志内容风格**：业务日志以 key=value 形式拼接，如 `Evicted agent: userId={}, cause={}`、`[CostControl] Model call #{} tokens: input={}, output={}, total={}, sessionTotal={}`，保持可读且可被 JSON 解析器提取
- **分层级别**：controller 层 INFO，agent/tool/middleware 层在 dev 下 DEBUG，security/web/hibernate SQL 单独调低级别避免噪音
- **输出目标**：开发/测试同时打印到控制台和文件；生产只写文件，减少 I/O 开销

## 4. 开发者应遵循的规则
- 使用 `@Slf4j` 注解引入 logger，不要手动 new Logger
- 关键上下文（用户、会话、请求追踪）建议通过 MDC 设置 `userId`、`sessionId`、`traceId`，以便 JSON 日志自动携带
- 日志级别选择：DEBUG 用于调试细节，INFO 记录业务事件（Agent 构建、工具调用、成本/Token 监控），WARN/ERROR 仅用于异常与降级路径
- 不要在日志中输出敏感信息（密码、token、PII）
- 如需调整输出格式或级别，优先修改 `application-{profile}.yml` 中的 `logging.*` 配置，而非直接改 logback-spring.xml