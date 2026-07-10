---
kind: error_handling
name: Spring Boot 全局异常处理体系
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/com/tutorial/offerpilot/exception/GlobalExceptionHandler.java
    - src/main/java/com/tutorial/offerpilot/common/ApiResponse.java
    - src/main/java/com/tutorial/offerpilot/exception/BusinessException.java
    - src/main/java/com/tutorial/offerpilot/exception/ResourceNotFoundException.java
    - src/main/java/com/tutorial/offerpilot/exception/RateLimitException.java
    - src/main/java/com/tutorial/offerpilot/exception/DuplicateUserException.java
---

## 错误处理架构概述

OfferPilot 项目采用 Spring Boot 的 `@RestControllerAdvice` + 自定义业务异常类的分层错误处理机制，实现了统一的异常捕获、日志记录和标准化响应格式。

## 核心组件

### 1. 统一响应格式 (`ApiResponse`)
- 位置：`common/ApiResponse.java`
- 结构：包含 `code`（状态码）、`message`（消息）、`data`（数据）三个字段
- 提供静态工厂方法：`success()`、`error(code, message)`
- 使用 `@JsonInclude(JsonInclude.Include.NON_NULL)` 自动过滤空字段

### 2. 基础业务异常类 (`BusinessException`)
- 位置：`exception/BusinessException.java`
- 继承 `RuntimeException`，支持运行时抛出
- 核心属性：`errorCode`（HTTP状态码）+ `message`（错误信息）
- 提供两个构造器：带状态码和默认400状态码

### 3. 具体业务异常类型
所有业务异常都继承自 `BusinessException`，形成清晰的异常层次结构：
- `ResourceNotFoundException` (404) - 资源不存在
- `RateLimitException` (429) - 频率限制
- `DuplicateUserException` (409) - 用户重复
- `FileUploadException` (400) - 文件上传失败
- `InvalidCredentialsException` - 凭证无效
- `AccountDisabledException` - 账户禁用

### 4. 全局异常处理器 (`GlobalExceptionHandler`)
- 位置：`exception/GlobalExceptionHandler.java`
- 使用 `@RestControllerAdvice` 实现全局拦截
- 处理的异常类型及策略：
  - `BusinessException` → 返回对应的业务状态码和消息
  - `MethodArgumentNotValidException` → 参数校验失败，合并多个字段错误
  - `IllegalArgumentException` → 服务器内部错误（500）
  - `Exception` → 兜底处理，记录完整堆栈日志

## 错误处理流程

1. **业务层抛出异常**：Service/Controller 中抛出具体业务异常
2. **全局拦截**：`GlobalExceptionHandler` 捕获并分类处理
3. **日志记录**：根据异常级别使用 `log.warn` 或 `log.error` 记录
4. **统一响应**：转换为 `ApiResponse` 格式返回给前端
5. **HTTP状态码**：直接使用异常中的 `errorCode` 作为 HTTP 状态码

## 使用模式

### 业务异常抛出示例：
```java
// 在 Service 层
throw new ResourceNotFoundException("知识库不存在");
throw new RateLimitException("请求过于频繁");
throw new BusinessException(403, "权限不足");
```

### 工具异常处理：
部分工具类使用 try-catch 包裹外部调用，将底层异常包装为业务异常或直接返回错误结果。

## 设计特点

1. **分层清晰**：业务异常与系统异常分离，便于精确控制
2. **状态码驱动**：通过 `errorCode` 直接映射到 HTTP 状态码
3. **统一格式**：所有错误响应遵循相同的 JSON 结构
4. **日志分级**：业务警告用 warn，系统错误用 error
5. **扩展性强**：新增异常类型只需继承 `BusinessException` 并指定状态码

## 注意事项

- 部分服务层仍在使用 `RuntimeException`，建议逐步迁移到业务异常
- 前端需要适配统一的 `ApiResponse` 错误处理逻辑
- 第三方 API 调用的异常需要适当包装为业务异常