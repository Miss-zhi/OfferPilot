## Context

OfferPilot 当前对话系统的问题：消息仅存于前端 Zustand 内存和 AgentScope Redis 会话上下文（TTL 30 分钟），无持久化。页面刷新、退出登录后历史对话全部丢失。

现有架构：Spring Boot 3 + JPA + MySQL，前端 Zustand + React + Ant Design。遵循项目四层架构（Controller → Service → Repository）。

## Goals / Non-Goals

**Goals:**
- 持久化所有对话消息（用户消息 + AI 回复，含 thinking 内容）
- 提供会话列表侧边栏（ChatGPT 式体验）：新建、切换、删除、重命名
- 支持按关键词全文搜索历史消息
- 不分页加载单会话全部消息

**Non-Goals:**
- 不实现对话分享链接（share link）
- 不实现分页加载消息
- 不修改 AgentScope 框架内部的 Redis 会话管理

## Decisions

### 1. 数据模型：两表设计（会话 + 消息）

```
op_chat_session                          op_chat_message
├── id (PK, BIGINT AUTO_INCREMENT)       ├── id (PK, BIGINT AUTO_INCREMENT)
├── session_id (UK, VARCHAR(64))         ├── session_id (FK → op_chat_session.session_id, INDEX)
├── user_id (VARCHAR(64), INDEX)         ├── role (VARCHAR(8): USER / AI)
├── title (VARCHAR(200))                 ├── content (MEDIUMTEXT)
├── active_function (VARCHAR(64))        ├── thinking_content (MEDIUMTEXT, nullable)
├── message_count (INT DEFAULT 0)        ├── tool_calls (JSON, nullable)
├── created_at (DATETIME)                ├── seq (INT)
└── updated_at (DATETIME)                ├── created_at (DATETIME)
                                         └── FULLTEXT INDEX ft_content ON (content, thinking_content)
```

**Why 两表而非 JSON 单表？** 支持按会话维度操作（删除会话级联删消息）、消息级全文搜索、增量追加（流式场景天然适配）。

**Why MEDIUMTEXT？** thinking 内容可能较长（上限 10000 字符），content 也可能很长。MEDIUMTEXT 支持 16MB，足够。

**Why FULLTEXT 而非 LIKE？** 支持中文分词搜索（需 MySQL 8.0 ngram parser），性能远优于 `LIKE '%keyword%'`。

### 2. 消息保存时机：混合策略

| 消息类型 | 保存时机 | 触发方 |
|---------|---------|--------|
| 用户消息 | `POST /stream` 收到请求后立即写入 | 后端 |
| AI 消息 | SSE `done` 事件后，前端 `onDone` 回调 | 前端 |

**Why 用户消息由后端保存？** 后端收到请求即可确认消息内容，同步写入最可靠。

**Why AI 消息由前端保存？** HITL 确认流程中 AI 回复跨两个 SSE 流，后端难以拼装完整消息（content + thinking + toolCalls）。前端 Zustand 天然持有最终完整态，在 `onDone` 时 `POST /messages` 最简洁。

### 3. API 设计

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/v1/offerpilot/chat/sessions` | 当前用户会话列表，按 updated_at DESC |
| POST | `/api/v1/offerpilot/chat/sessions` | 创建新会话 `{activeFunction: "mock_interview"}` → 返回 sessionId |
| DELETE | `/api/v1/offerpilot/chat/sessions/{sessionId}` | 删除会话 + 级联删除所有消息 |
| PATCH | `/api/v1/offerpilot/chat/sessions/{sessionId}` | 编辑会话标题 `{title: "..."}` |
| GET | `/api/v1/offerpilot/chat/sessions/{sessionId}/messages` | 该会话全部消息，按 seq ASC |
| POST | `/api/v1/offerpilot/chat/sessions/{sessionId}/messages` | 保存一条消息 |
| GET | `/api/v1/offerpilot/chat/sessions/search?q=keyword` | 全文搜索，返回匹配的会话列表 + 摘要 |

所有端点遵循项目 RESTful 规范（Rustful API 设计规范：小写+中划线、统一响应格式）。

### 4. 会话标题生成

自动取首条用户消息前 30 字符作为标题，存储时截断。用户可通过 PATCH 端点重命名。

### 7. seq 字段生成

会话内消息序号，从 1 开始递增。Service 层保存消息时先查询 `SELECT COALESCE(MAX(seq), 0) + 1 FROM op_chat_message WHERE session_id = ?`，保证同一会话内 seq 唯一且连续。

### 5. 前端多会话管理

在现有 `chat-store.ts` 基础上扩展：
- 新增 `sessions: SessionListItem[]` 状态
- 新增 `currentSessionId` 标识当前活跃会话
- 新增 `loadSessions()`、`switchSession(sessionId)`、`deleteSession(sessionId)` action
- 新建会话时先 `POST /sessions` 获取 sessionId，再发送第一条消息

### 6. 搜索 UX

搜索返回匹配的会话列表，每条包含：sessionId、sessionTitle、matchSnippet（匹配片段摘要）、matchCount（命中次数）。点击进入该会话加载完整消息。

## Risks / Trade-offs

- **[AI 消息丢失风险]** 前端 `onDone` 后若网络异常，AI 消息可能未保存 → 前端在 `onDone` 后增加重试逻辑（3 次，间隔递增）
- **[FULLTEXT 中文分词]** MySQL ngram token size 默认为 2（双字分词），中文搜索基本够用 → 保持默认值 2，无需修改 MySQL 配置。`ngram_token_size=1` 会产生巨量 token，性能开销大，不推荐。后续搜索质量不足可迁移到 Elasticsearch。
- **[会话列表膨胀]** 无分页，大量会话时前端性能 → 当前用户量级较小，后续可加分页
- **[sessionId 一致性]** 现有 `resolveSessionId()` 在 `ChatController` 中生成首条消息的 sessionId，前端在第一轮 SSE 事件中收到。多会话场景下前端需在新会话页面上先调 `POST /sessions` 获取 sessionId → 修改 `ChatPage` 的 `handleSend` 逻辑
- **[并发切换安全]** AI 消息 `onDone` → `saveMessage` 是异步的，若用户此时快速切换会话，可能将消息存到错误的 sessionId → 在 `onDone` 回调中**闭包捕获**当前的 `sessionId`，不从 store 动态读取

## Open Questions

<!-- None at this stage -->
