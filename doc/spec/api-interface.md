# API 接口设计

> **来源**：《03-详细设计说明书》§3  
> **模块**：RESTful API 接口  
> **约定**：所有接口返回 `ApiResponse<T>`，失败由全局异常处理器返回 `{ "code": xxx, "message": "xxx" }`

---

## 3.1 认证接口

```
POST /api/auth/register
Content-Type: application/json

请求体:
{
  "username": "zhangsan",
  "password": "MyP@ssw0rd",
  "email": "zhangsan@example.com"
}

响应 (201):
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "u-20260708-001",
  "username": "zhangsan",
  "role": "USER"
}

POST /api/auth/login
Content-Type: application/json

请求体:
{
  "username": "zhangsan",
  "password": "MyP@ssw0rd"
}

响应 (200):
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "u-20260708-001",
  "username": "zhangsan",
  "role": "USER"
}
```

## 3.2 对话接口（SSE 流式）

```
GET /api/offerpilot/chat/stream
    ?message=帮我分析一下这个面试录音
    &sessionId=session-20260708-001
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Accept: text/event-stream

事件流:
event: TEXT_BLOCK_DELTA
data: {"delta": "好的，我来帮你分析"}

event: TOOL_CALL_START
data: {"tool": "transcribe_audio", "input": {"file_path": "uploads/interview.mp3"}}

event: TOOL_CALL_END
data: {"tool": "transcribe_audio", "output": "...(转写结果)"}

event: TEXT_BLOCK_DELTA
data: {"delta": "录音已转写，共 5 个问答对。开始分析..."}

event: SUBAGENT_SPAWN
data: {"agent": "tech_evaluator", "task": "分析第1题 HashMap"}

event: TEXT_BLOCK_DELTA
data: {"delta": "## Q1 HashMap 底层原理\n技术深度：75/100\n..."}

event: AGENT_END
data: {"status": "completed"}
```

## 3.3 同步对话接口

```
POST /api/offerpilot/chat
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

请求体:
{
  "message": "帮我看看这份简历有什么问题",
  "sessionId": "session-20260708-001"
}

响应:
{
  "reply": "你的简历整体结构清晰，但有 3 个可以优化的地方...",
  "sessionId": "session-20260708-001"
}
```

## 3.4 文件上传接口

```
POST /api/offerpilot/upload
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: multipart/form-data

file: [binary]
type: resume | interview_audio | interview_text

响应:
{
  "fileId": "file-20260708-001",
  "filePath": "uploads/resume_zhangsan.pdf",
  "fileType": "resume",
  "size": 245760
}
```

## 3.5 分析报告接口

```
GET /api/offerpilot/reports/{reportId}
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

响应:
{
  "reportId": "rpt-20260708-001",
  "type": "interview_analysis",
  "overallScore": 72,
  "dimensions": {
    "technical_depth": 75,
    "expression_logic": 60,
    "knowledge_coverage": 70,
    "confidence": 68,
    "time_management": 80,
    "highlights": 78
  },
  "questionDetails": [...],
  "improvements": ["系统复习 Redis 相关知识", "练习先结论后展开的表达方式"]
}
```

## 3.6 用户进度接口

```
GET /api/offerpilot/progress?range=month
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

响应（自动从 Token 中获取 userId）:
{
  "period": "month",
  "interviewCount": 4,
  "scoreTrend": [65, 72, 78, 83],
  "knowledgeMastery": {
    "HashMap": { "first": 50, "current": 82, "trend": "up" },
    "Spring事务": { "first": 60, "current": 85, "trend": "up" }
  },
  "studyPlan": { "completed": 12, "total": 20 }
}
```

## 3.7 知识库管理 API（11 个端点）

```
POST   /api/admin/kb                           创建知识库（ADMIN→公共，USER→私有）
GET    /api/admin/kb                            知识库列表（ADMIN看全部，USER看公共+自己的）
DELETE /api/admin/kb/{kbId}                     删除知识库（级联删除文档和向量）
POST   /api/admin/kb/{kbId}/docs                上传文档（支持多文件）
GET    /api/admin/kb/{kbId}/docs                文档列表（分页+状态过滤）
GET    /api/admin/kb/{kbId}/docs/{docId}        文档详情（含分块预览）
DELETE /api/admin/kb/{kbId}/docs/{docId}        删除文档（同时清理 MySQL + Milvus）
POST   /api/admin/kb/{kbId}/docs/{docId}/reindex 重建索引（可更换分块策略）
POST   /api/admin/kb/{kbId}/search              检索测试
GET    /api/admin/kb/{kbId}/docs/{docId}/progress 查询文档处理进度
GET    /api/admin/kb/{kbId}/stats                统计信息
```

## 3.8 薪资谈判接口

```
GET /api/offerpilot/salary/search
    ?company=字节跳动
    &position=Java后端开发
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

响应:
{
  "total": 15,
  "salaries": [
    {
      "company": "字节跳动",
      "position": "Java后端开发",
      "baseRange": "25k-40k × 15",
      "bonusRange": "3-6个月",
      "stockInfo": "期权 4年归属",
      "source": "牛客 offerShow",
      "relevanceScore": 0.92
    }
  ]
}

POST /api/offerpilot/salary/compare
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

请求体:
{
  "offers": [
    {
      "company": "字节跳动",
      "position": "Java后端开发",
      "base": 30,
      "months": 15,
      "bonus": "3-6个月",
      "stock": "期权 4年归属",
      "location": "北京"
    },
    {
      "company": "阿里巴巴",
      "position": "Java开发工程师",
      "base": 28,
      "months": 16,
      "bonus": "2-4个月",
      "stock": "RSU 4年归属",
      "location": "杭州"
    }
  ]
}

响应:
{
  "summary": "字节跳动总包约 45-54 万/年，阿里巴巴总包约 44.8-51.2 万/年...",
  "analyses": [...],
  "recommendation": "综合来看，字节跳动现金部分更高，但阿里 RSU 流动性更好..."
}

POST /api/offerpilot/salary/negotiation-script
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

请求体:
{
  "currentOffer": "字节跳动 30k×15 + 期权...",
  "targetSalary": 50,
  "negotiationStyle": "moderate"
}

响应:
{
  "openingLine": "非常感谢贵公司的认可，我对这个机会非常期待...",
  "talkingPoints": ["基于市场数据，同级别岗位的薪资中位数为..."],
  "counterArguments": ["如果对方说'这是我们能给的最好方案'，可以回应..."],
  "closingLine": "期待您的回复，谢谢！"
}
```

## 3.9 会话重置接口

```
POST /api/offerpilot/session/reset
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

响应:
{
  "sessionId": "session-new-20260708",
  "message": "会话已重置"
}
```
