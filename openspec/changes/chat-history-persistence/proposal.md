## Why

当前聊天系统的对话消息仅存储在前端 Zustand 内存和 Redis（AgentScope 会话上下文，TTL 30 分钟），没有任何持久化机制。用户刷新页面、退出登录或 Redis 过期后，所有对话记录彻底丢失，无法回溯历史对话。

## What Changes

- **新增** 数据库表 `op_chat_session`（会话）和 `op_chat_message`（消息），持久化存储所有对话
- **新增** REST API 端点：会话 CRUD、消息保存/查询、全文搜索
- **修改** `ChatController`：在 SSE 流中自动保存用户消息，AI 消息由前端 `onDone` 回调保存
- **修改** 前端 `chat-store.ts`：支持多会话管理（列表、切换、加载、新建、删除）
- **新增** 前端 `SessionList` 侧边栏组件：展示历史会话列表，支持搜索、重命名、删除
- **修改** `ChatPage.tsx`：集成侧边栏会话列表，支持会话切换
- **修改** `chatService.ts`：新增会话管理 API 调用

## Capabilities

### New Capabilities

- `chat-session-management`: 会话列表展示、新建、删除、重命名、切换
- `chat-message-persistence`: 对话消息持久化保存（含 thinking 内容）、加载、全文搜索

### Modified Capabilities

<!-- No existing specs require modification -->

## Impact

- **数据库**：新增 2 张表（`op_chat_session`、`op_chat_message`），需执行 DDL
- **后端**：新增 `ChatSession` / `ChatMessage` Entity + Repository + Service + DTO；修改 `ChatController`
- **前端**：新增 `SessionList` 组件；修改 `chat-store.ts`、`chatService.ts`、`ChatPage.tsx`
- **依赖**：无新增外部依赖
- **兼容性**：客户端消息保存依赖前端 `onDone` 回调触发，不影响现有 SSE 流式对话逻辑
