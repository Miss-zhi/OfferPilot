---
kind: logging_system
name: 基于 Logback + Logstash 的结构化日志系统
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/logback-spring.xml
    - src/main/resources/application.yml
---

## 1. 使用的系统与框架
- **日志框架**：SLF4J + Logback（Spring Boot 默认）
- **结构化输出**：`net.logstash.logback.encoder.LogstashEncoder`，将文件日志输出为 JSON 格式的 Logstash/ELK 兼容格式
- **注解注入**：统一使用 Lombok `@Slf4j` 在类上声明 logger，通过 `log.info/debug/warn/error` 调用
- **异步写入**：通过 `AsyncAppender` 包装 RollingFileAppender，队列大小 512、永不阻塞

## 2. 核心配置文件
- `src/main/resources/logback-spring.xml`：Logback 主配置，定义 Appender、RollingPolicy、MDC 字段、Profile 级别
- `src/main/resources/application.yml`：Spring Boot 侧的 logging.* 属性（file.path/name、level、pattern），被 logback-spring.xml 通过 `<springProperty>` 读取
- `application-dev.yml` / `application-prod.yml`：按 Profile 覆盖部分配置（当前未单独重写 logging 块）

## 3. 架构与约定
### 3.1 Appender 策略
| Appender | 用途 | 说明 |
|---|---|---|
| CONSOLE | 开发调试控制台输出 | 彩色高亮 pattern，仅 dev/test 启用 |
| FILE | 滚动文件输出 | 按天+大小滚动，单文件最大 10MB，保留 7 天，总上限 500MB |
| ASYNC_FILE | 异步文件输出 | 包裹 FILE，neverBlock=true，生产环境 root 仅指向此 |

### 3.2 结构化字段（MDC）
LogstashEncoder 显式 include 以下 MDC key，期望业务代码通过 `MDC.put(key, value)` 设置：
- `userId` — 用户标识
- `sessionId` — 会话标识
- `traceId` — 链路追踪 ID
同时自动附加自定义字段：`app=offerpilot`、`env=dev/prod`。

### 3.3 日志级别策略
- **dev/test**：root=INFO；`com.tutorial.offerpilot`=DEBUG；Security/Web=DEBUG/INFO；Hibernate SQL=WARN
- **prod**：root=INFO；业务包=INFO；Security=WARN；关闭控制台输出
- Spring Boot 层额外在 `application.yml` 中声明了 `io.agentscope: INFO`、`io.milvus: INFO` 等第三方包级别

### 3.4 编码与字符集
- Console：UTF-8，带 `%highlight` 颜色
- File：Logstash JSON 格式，便于 ELK 解析
- 业务代码中的日志消息采用键值对风格（如 `"Evicted agent: userId={}, cause={}"`），而非纯文本描述

## 4. 开发者应遵循的规则
1. **统一使用 `@Slf4j`**：不要在类内手动 `LoggerFactory.getLogger(...)`，所有需要日志的类加 `@Slf4j` 注解即可。
2. **使用结构化键值对**：日志消息以 `key=value` 形式组织，避免长段自然语言，方便后续 JSON 解析与检索。
3. **主动填充 MDC**：在请求入口（建议 Filter/Interceptor）设置 `MDC.put("userId", ...)`, `MDC.put("sessionId", ...)`, `MDC.put("traceId", ...)`，确保文件日志中包含这些字段。
4. **按场景选择级别**：
   - `info`：关键业务流程节点（Agent 构建、工具调用开始/结束、模型调用计数等）
   - `warn`：可恢复异常或阈值告警（成本超限、Token 超限、回退到默认模型等）
   - `error`：不可恢复错误（数据库连接失败、外部 API 异常等）
   - `debug`：仅在 dev 下开启的详细诊断信息
5. **不要直接打印敏感信息**：密码、API Key、完整 Token 等不应出现在日志中。
6. **生产环境只依赖 ASYNC_FILE**：Console Appender 仅用于本地开发，生产部署时不应输出到 stdout/stderr。