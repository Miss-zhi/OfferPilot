# OfferPilot 模块级 Spec 文件索引

> **来源**：从《03-详细设计说明书》按模块拆分  
> **版本**：v5.0

---

## 模块 Spec 列表

| # | 模块 | 文件 | 说明 |
|:--|:-----|:-----|:-----|
| 1 | RAG 知识库 | [rag-knowledge-base.md](rag-knowledge-base.md) | 向量存储选型、Milvus Collection 设计、分块策略、Embedding 服务、异步入库管道、检索策略 |
| 2 | 多租户知识库 | [multi-tenant-kb.md](multi-tenant-kb.md) | 公共库 + 私有库两层架构、多租户检索逻辑、管理权限矩阵 |
| 3 | API 接口 | [api-interface.md](api-interface.md) | 认证、对话（SSE/同步）、文件上传、分析报告、用户进度、知识库管理（11 端点）、薪资谈判、会话重置 |
| 4 | 数据库 Schema | [database-schema.md](database-schema.md) | 18 张表 DDL（9 业务 + 4 知识库 + 2 安全 + 1 检索日志 + 1 定时任务 + 1 关系说明） |
| 5 | 用户长期记忆 | [user-memory.md](user-memory.md) | 记忆分类、UserMemoryService、MemoryInjectMiddleware 集成方式 |
| 6 | 前端页面原型 | [frontend-pages.md](frontend-pages.md) | 9 个核心页面线框图（登录、对话、报告、成长追踪、简历诊断、知识库管理） |

## 模块依赖关系

```
rag-knowledge-base ──→ multi-tenant-kb
        ↓                    ↓
  database-schema      api-interface
        ↓                    ↓
   user-memory        frontend-pages
```

## 关联文档

- 需求规格：`doc/01-需求规格说明书.md`
- 系统架构：`doc/02-系统架构设计说明书.md`
- 实现规范：`doc/04-实现与编码规范.md`
- 部署运维：`doc/05-部署运维手册.md`
