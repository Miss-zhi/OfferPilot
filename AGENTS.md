# OfferPilot — AI 开发上下文

## 项目概述

OfferPilot 是面向智能求职辅导/面试场景的 AI 应用平台，基于 Spring Boot 3 + AgentScope Java v2 构建。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.2.5（Servlet MVC） |
| AI Agent 框架 | AgentScope Java v2（ReActAgent） |
| 数据库 | MySQL 8.0（JPA + Hibernate） |
| 缓存/限流 | Redis 7（Spring Data Redis） |
| 向量数据库 | Milvus 2.4.6（RAG 检索） |
| 对象存储 | MinIO（Milvus 依赖 + 文件上传） |
| 认证 | Spring Security + JWT（jjwt） |
| LLM 服务 | DashScope（阿里云通义系列） |
| 构建工具 | Maven |

## 项目结构

单模块 Spring Boot 项目，包名 `com.tutorial.offerpilot`：

```
src/main/java/com/tutorial/offerpilot/
├── OfferPilotApplication.java      # 启动类
├── common/                          # 公共基础类（BaseEntity, ApiResponse, PageRequest）
├── enums/                           # 枚举（UserRole, Visibility, DocumentStatus 等）
├── config/                          # Spring 配置类（Security, Milvus, Redis 等）
├── security/                        # JWT 认证/鉴权（JwtTokenProvider, Filter）
├── controller/                      # RESTful Controller（@RestController）
├── service/                         # 业务逻辑层
│   └── ingestion/                   # 异步入库管道（文档解析/分块/Embedding）
├── agent/                           # AgentScope 框架层
│   ├── AgentFactory.java           # Agent 构建 + 池管理（Caffeine）
│   ├── tool/                       # @Tool 工具类（13 个本地工具）
│   └── middleware/                 # MiddlewareBase（TokenMonitor, CostControl）
├── entity/                          # JPA 实体（18 张表）
├── repository/                      # Spring Data JPA Repository
├── dto/                             # 请求/响应 DTO（含 @Valid 注解）
│   ├── auth/                        # 认证相关 DTO
│   ├── chat/                        # 聊天相关 DTO
│   ├── kb/                          # 知识库相关 DTO
│   └── tool/                        # @Tool 方法返回 POJO
├── converter/                       # Entity ↔ DTO 转换
└── exception/                       # 异常体系（BusinessException + GlobalExceptionHandler）
```

## 关键约定

- 所有 Controller 方法返回 `ApiResponse<T>`
- Entity 继承 `BaseEntity`（id, createdAt, updatedAt, createBy, updateBy）
- Repository 继承 `JpaRepository<Entity, Long>`
- @Tool 工具方法返回 POJO（禁止返回 String JSON）
- 测试数据 `create_by = 'test'` 或 `id >= 99000`
- 基础设施通过 Docker Compose 本地运行（`docker-compose.yml`）

## 数据库

- 库名：`offerpilot`
- 主表：app_user, kb_knowledge_base, kb_document, kb_chunk, interview_session, user_memory 等
- ORM：Spring Data JPA（非 MyBatis）
