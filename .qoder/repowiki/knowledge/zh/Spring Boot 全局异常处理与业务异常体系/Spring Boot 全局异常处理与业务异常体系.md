---
kind: error_handling
name: Spring Boot 全局异常处理与业务异常体系
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/com/tutorial/offerpilot/exception/GlobalExceptionHandler.java
    - src/main/java/com/tutorial/offerpilot/exception/BusinessException.java
    - src/main/java/com/tutorial/offerpilot/common/ApiResponse.java
    - src/main/java/com/tutorial/offerpilot/service/AuthService.java
    - src/main/java/com/tutorial/offerpilot/controller/ChatController.java
---

## 错误处理架构概述

该项目采用 Spring Boot 标准的 @RestControllerAdvice + 自定义业务异常类的分层错误处理机制，通过统一的响应格式对外暴露错误信息。

## 核心组件

### 1. 统一响应封装 (ApiResponse)
位于 common/ApiResponse.java，提供标准化的成功/失败响应结构。包含 code、message、data 三个字段，使用 Jackson 的 NON_NULL 过滤。提供静态工厂方法：success()、success(data)、error(code, message)。

### 2. 全局异常处理器 (GlobalExceptionHandler)
位于 exception/GlobalExceptionHandler.java，使用 @RestControllerAdvice 注解。处理以下异常类型：
- BusinessException: 业务逻辑异常，返回对应的 HTTP 状态码
- MethodArgumentNotValidException: 参数校验失败，拼接所有字段错误信息
- IllegalArgumentException: 非法参数，返回 500 内部错误
- Exception: 兜底捕获所有未处理异常，记录详细日志并返回通用错误消息

### 3. 业务异常体系
基于 BusinessException 构建的分层异常类：
- 基础异常: BusinessException - 继承 RuntimeException，包含 errorCode 和 message
- 资源异常: ResourceNotFoundException (404) - 资源不存在
- 认证异常: InvalidCredentialsException (401) - 用户名或密码错误
- 权限异常: AccountDisabledException - 账户被禁用
- 冲突异常: DuplicateUserException (409) - 用户已存在
- 限流异常: RateLimitException (429) - 请求频率过高
- 文件异常: FileUploadException (400) - 文件上传相关错误

## 使用模式

服务层异常抛出示例：
if (userRepository.existsByUsername(req.getUsername())) {
    throw new DuplicateUserException();
}

外部依赖异常包装：
try {
    // 文件操作
} catch (IOException e) {
    throw new FileUploadException("文件保存失败: " + e.getMessage());
}

Controller 层限流控制：
if (!rateLimitService.tryAcquireDialogue(userId)) {
    throw new RateLimitException("对话频率过高，请稍后再试");
}

Agent 工具异常处理广泛使用 try-catch 包裹外部调用，将底层异常转换为业务异常。

## SSE 流式特殊处理
在 ChatController 的 SSE 流式处理中，采用特殊的错误处理策略：
- 使用 safeSend 函数包装发送逻辑，捕获 IOException 和 IllegalStateException
- 通过 AtomicBoolean completed 标志位确保幂等性
- 在 doFinally 回调中确保资源释放和连接关闭

## 设计决策

1. 异常分类清晰：按业务领域划分异常类型，便于精确的错误处理
2. HTTP 语义映射：每个异常对应明确的 HTTP 状态码，符合 RESTful 规范
3. 日志分级合理：业务异常使用 warn 级别，系统异常使用 error 级别
4. 错误信息友好：对外返回用户友好的错误消息，内部保留详细技术信息
5. SSE 健壮性：针对流式通信的特殊场景设计了完善的错误恢复机制

## 开发约定

- 业务逻辑异常应抛出具体业务异常类，而非直接使用 BusinessException
- 外部依赖异常应在适当层级进行包装，避免泄露底层实现细节
- 所有异常都应记录足够的上下文信息用于问题排查
- SSE 流处理必须确保连接资源的正确释放