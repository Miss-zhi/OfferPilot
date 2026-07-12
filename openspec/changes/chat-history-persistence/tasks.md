## 1. 数据库

- [x] 1.1 编写 DDL 建表脚本：`op_chat_session`（session_id, user_id, title, active_function, message_count）+ `op_chat_message`（session_id, role, content, thinking_content, tool_calls JSON, seq）+ FULLTEXT INDEX，输出到 `Documents/Sql变更/`
- [x] 1.2 在测试环境执行 DDL，验证表结构正确

## 2. 后端 Domain 层

- [x] 2.1 创建 `ChatSession` Entity（继承 BaseEntity，映射 `op_chat_session` 表）
- [x] 2.2 创建 `ChatMessage` Entity（继承 BaseEntity，映射 `op_chat_message` 表）
- [x] 2.3 创建 `ChatSessionRepository`（JpaRepository，按 userId 查询 + 按 sessionId 查询 + 按 updatedAt 排序 + 删除）
- [x] 2.4 创建 `ChatMessageRepository`（JpaRepository，按 sessionId + seq 排序查询 + 批量删除 + FULLTEXT 搜索）

## 3. 后端 DTO 层

- [x] 3.1 创建 `CreateSessionRequest` DTO（activeFunction）
- [x] 3.2 创建 `SessionListItem` DTO（sessionId, title, activeFunction, messageCount, createdAt, updatedAt）
- [x] 3.3 创建 `MessageSaveRequest` DTO（role, content, thinkingContent, toolCalls）
- [x] 3.4 创建 `RenameSessionRequest` DTO（title）
- [x] 3.5 创建 `SearchResultItem` DTO（sessionId, sessionTitle, matchSnippet, matchCount）

## 4. 后端 Service 层

- [x] 4.1 创建 `ChatHistoryService`：listSessions(userId)、createSession(userId, activeFunction)、deleteSession(sessionId)、renameSession(sessionId, title)、getMessages(sessionId)、saveMessage(sessionId, request)、searchMessages(userId, keyword)
- [x] 4.2 实现消息序号：保存消息前查询 `SELECT COALESCE(MAX(seq), 0) + 1` 生成 seq
- [x] 4.3 实现 createBy 手动设置：`entity.setCreateBy(userId)`（项目无 AuditorAware）
- [x] 4.4 实现会话标题自动生成（取首条用户消息前 30 字符）
- [x] 4.5 实现 FULLTEXT 搜索查询（MATCH AGAINST with BOOLEAN MODE + ngram parser）

## 5. 后端 Controller 层

- [x] 5.1 新增会话管理端点：GET/POST/DELETE/PATCH `/api/v1/offerpilot/chat/sessions`
- [x] 5.2 新增消息端点：GET/POST `/api/v1/offerpilot/chat/sessions/{sessionId}/messages`
- [x] 5.3 新增搜索端点：GET `/api/v1/offerpilot/chat/sessions/search?q=keyword`
- [x] 5.4 修改 `chatStream()` 方法：收到请求后在流程开始前保存用户消息到 DB
- [x] 5.5 修改 `chatStream()` 方法：自动创建 session（若 sessionId 不存在）

## 6. 前端 API 层

- [x] 6.1 新增 `chatService` 方法：`listSessions()`、`createSession()`、`deleteSession(sessionId)`、`renameSession(sessionId, title)`、`getMessages(sessionId)`、`saveMessage(sessionId, data)`、`searchSessions(keyword)`
- [x] 6.2 新增 `API` 常量路径配置

## 7. 前端 Store 层

- [x] 7.1 扩展 `ChatState`：新增 `sessions: SessionListItem[]`、`currentSessionId: string | null`
- [x] 7.2 新增 action：`loadSessions()`、`switchSession(sessionId)`、`createNewSession()`、`deleteSession(sessionId)`、`renameSession(sessionId, title)`
- [x] 7.3 修改 `resetSession`：改为创建新会话而非清空

## 8. 前端 SessionList 组件

- [x] 8.1 创建 `SessionList.tsx` 侧边栏组件：会话列表渲染、搜索框、新建按钮
- [x] 8.2 实现会话项交互：点击切换、双击/右键重命名、删除确认
- [x] 8.3 实现搜索功能：防抖输入 → 调后端搜索 API → 展示匹配结果

## 9. 前端 ChatPage 集成

- [x] 9.1 修改 `ChatPage.tsx` 布局：左侧 Sider 嵌入 SessionList，右侧聊天区域
- [x] 9.2 修改 `handleSend`：新建会话时先调 `POST /sessions` 获取 sessionId 再发流
- [x] 9.3 修改 `onDone` 回调：AI 回复完成后调 `saveMessage` 保存消息（含重试逻辑 3 次），**闭包捕获** `sessionId` 防止并发切换写错会话
- [x] 9.4 新增 `onSessionChange`：切换会话时加载历史消息到消息列表
