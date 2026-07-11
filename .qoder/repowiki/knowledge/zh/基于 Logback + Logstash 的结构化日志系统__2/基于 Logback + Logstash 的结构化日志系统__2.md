---
kind: logging_system
name: 基于 Logback + Logstash 的结构化日志系统
category: logging_system
scope:
    - '**'
source_files:
    - src/main/resources/logback-spring.xml
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
    - src/main/resources/application-prod.yml
    - src/main/java/com/tutorial/offerpilot/exception/GlobalExceptionHandler.java
    - src/main/java/com/tutorial/offerpilot/controller/AuthController.java
    - src/main/java/com/tutorial/offerpilot/agent/tool/MockInterviewTool.java
---

## 日志系统概述

OfferPilot 项目采用 Spring Boot 内置的 Logback 作为日志框架，结合 Logstash Encoder 实现结构化日志输出，支持多环境配置和异步文件滚动。

## 核心架构

### 日志框架与依赖
- 框架: Logback (Spring Boot 默认)
- 编码器: LogstashEncoder (net.logstash.logback.encoder.LogstashEncoder)
- 注解: Lombok @Slf4j
- MDC支持: 包含 userId、sessionId、traceId 字段

### 日志输出目标
1. 控制台输出 (CONSOLE): 开发环境使用彩色格式化输出
2. 异步文件输出 (ASYNC_FILE): 生产环境主要输出目标
3. 滚动策略: 按天滚动 + 大小限制 (10MB) + 保留7天 + 总大小500MB上限

### 配置文件结构
- logback-spring.xml: 主日志配置，定义 Appender、Pattern、Profile
- application.yml: 基础日志级别配置
- application-dev.yml: 开发环境 DEBUG 级别
- application-prod.yml: 生产环境 INFO/WARN 级别

## 日志级别策略

### 开发环境 (dev/test)
- Root: INFO
- 业务包: DEBUG
- Spring Security: DEBUG
- Spring Web: INFO
- Hibernate SQL: WARN

### 生产环境 (prod)
- Root: INFO
- 业务包: INFO
- Spring Security: WARN
- 仅文件输出，无控制台

## 结构化日志规范

### 日志格式
- 控制台: %d{HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n
- 文件: Logstash JSON 格式，包含自定义字段 app、env 和 MDC 字段

### 日志内容规范
- 使用参数化日志：log.info("Login request: username={}", username)
- 关键业务操作记录：用户注册、登录、工具调用等
- 异常处理：统一在 GlobalExceptionHandler 中记录
- Agent 工具调用：详细记录输入输出参数

### MDC 上下文
配置文件声明了三个 MDC 字段用于结构化追踪：
- userId: 用户标识
- sessionId: 会话标识
- traceId: 请求追踪ID

## 使用模式

### 标准日志注入
@Slf4j
public class XxxService {
    public void method() {
        log.info("业务操作: param={}", value);
        log.warn("警告信息: detail={}", detail);
        log.error("错误信息", exception);
    }
}

### 日志使用场景
- Controller层: 记录HTTP请求入口参数
- Service层: 记录业务逻辑执行状态
- Agent Tools: 记录AI工具调用详情
- Middleware: 记录成本控制和Token监控信息
- Exception处理: 统一异常日志记录

## 性能优化
- 异步Appender: queueSize=512, neverBlock=true
- 文件滚动: TimeBasedRollingPolicy + SizeAndTimeBasedFNATP
- 压缩存储: .gz 格式归档
- 容量控制: maxHistory=7, totalSizeCap=500MB