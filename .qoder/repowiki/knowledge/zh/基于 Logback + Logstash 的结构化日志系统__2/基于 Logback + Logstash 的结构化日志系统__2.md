---
kind: logging_system
name: 基于 Logback + Logstash 的结构化日志系统
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/logback-spring.xml
---

## 1. 使用的系统与框架
- 日志实现：Logback（Spring Boot 默认）
- 结构化输出：net.logstash.logback.encoder.LogstashEncoder，将文件日志输出为 JSON（Logstash/ELK 友好格式）
- 控制台与文件双 Appender，生产环境仅写文件，开发/测试环境同时输出到控制台
- 异步写入：通过 AsyncAppender 包裹 RollingFileAppender，队列大小 512、永不阻塞、丢弃阈值关闭

## 2. 核心配置文件
- src/main/resources/logback-spring.xml：唯一日志配置入口，定义 Appender、RollingPolicy、Profile 级别、MDC 字段注入
- application.yml / application-dev.yml / application-prod.yml：通过 logging.file.path、logging.file.name、spring.application.name、spring.profiles.active 等属性驱动 Logback 行为

## 3. 架构与约定
- 多 Appender 分层
  - CONSOLE：彩色人类可读格式，仅 dev/test 启用
  - FILE：按天滚动 + 单文件最大 10MB，保留 7 天、总容量 500MB，JSON 结构包含自定义字段 app、env 以及 MDC 中的 userId、sessionId、traceId
  - ASYNC_FILE：对 FILE 的异步封装，避免 I/O 阻塞业务线程
- Profile 分级策略
  - dev,test：root=INFO，com.tutorial.offerpilot=DEBUG，org.springframework.security=DEBUG，org.hibernate.SQL=WARN
  - prod：root=INFO，应用包与 Security 均降至 INFO/WARN，减少 IO 压力
- MDC 上下文字段：通过 includeMdcKeyName 声明式把 userId、sessionId、traceId 打入每条 JSON 日志，便于跨服务链路追踪
- 编码规范：业务代码统一使用 Lombok @Slf4j 注解生成 logger，以 log.info/warn/error/debug 调用；关键路径（Agent 构建、模型调用、成本/Token 监控）集中记录结构化参数（agent、session、model、token 用量等）

## 4. 开发者应遵循的规则
- 使用 @Slf4j 注入 logger，不要手写 LoggerFactory.getLogger(...)
- 日志消息采用占位符 {} 传参，禁止字符串拼接
- 在需要关联请求上下文的类中，于入口方法前向 MDC 放入 userId、sessionId、traceId，确保 JSON 日志可被聚合检索
- 控制日志级别：正常流程用 info，异常/降级用 warn/error，调试信息用 debug 且仅在 dev 可见
- 避免打印敏感数据（密码、完整 token），只输出必要标识
- 新增外部依赖（如 MCP、向量库）时，在其初始化/错误分支补充结构化日志，保持与现有风格一致