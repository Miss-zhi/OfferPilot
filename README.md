# OfferPilot — AI 面试教练平台

基于 **Spring Boot 3 + AgentScope Java v2** 的全栈 AI 面试辅导平台，覆盖简历诊断、模拟面试、录音复盘和知识检索四大核心环节。

## 核心功能

| 功能 | 说明 |
|------|------|
| 📄 **简历智能诊断** | 上传 PDF 简历 → 自动解析 → 多维度评估 → 优化建议 |
| 🎙️ **AI 模拟面试** | 4 种面试模式（技术深挖/行为面试/系统设计/压力面试），SSE 流式对话 |
| 📊 **面试录音复盘** | 上传录音 → 转写为文字 → 六维深度分析（技术/表达/盲区/时长/自信度/亮点） |
| 🔍 **知识检索问答** | 多路 RAG 检索 → RRF 融合 → Reranker 精排，知识库不足时自动联网兜底 |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.2.5 (Servlet MVC) |
| 前端框架 | React 19 + Ant Design 6 + Vite |
| AI Agent | AgentScope Java v2 (HarnessAgent) |
| 数据库 | MySQL 8.0 + Redis 7 (H2 开发兼容) |
| 向量数据库 | Milvus 2.4.6 (RAG 向量检索) |
| 对象存储 | MinIO |
| 认证 | Spring Security + JWT (jjwt 0.12.5) |
| LLM | DashScope / DeepSeek / 多 Provider 可切换 |
| Embedding | DashScope text-embedding-v3 (1024 维) |
| ASR | DashScope Paraformer |
| 构建 | Maven + npm |

## 系统架构

```
┌──────────────────────────────────────────────────────┐
│               React 19 + Ant Design 6                │
│          SSE 流式对话 / 文件上传 / 仪表盘             │
└───────────────────────┬──────────────────────────────┘
                        │ HTTP (Bearer JWT)
┌───────────────────────▼──────────────────────────────┐
│              Spring Boot 3 应用层                     │
│                                                      │
│  ┌─────────────────────────────────────────────────┐ │
│  │       Spring Security + JWT 无状态认证           │ │
│  └───────────────────┬─────────────────────────────┘ │
│                      │                                │
│  ┌───────────────────▼─────────────────────────────┐ │
│  │   单 Agent 全能助手 (HarnessAgent)               │ │
│  │   10 个本地 @Tool + MCP 联网搜索                 │ │
│  └────────┬───────────────────┬────────────────────┘ │
│           │                   │                       │
│  ┌────────▼──────┐ ┌─────────▼──────────┐           │
│  │  MySQL (17表)  │ │  Milvus (向量检索) │           │
│  └───────────────┘ └────────────────────┘           │
│  ┌────────────────┐ ┌────────────────────┐          │
│  │  Redis (缓存)   │ │  MinIO (文件存储)   │          │
│  └────────────────┘ └────────────────────┘          │
└──────────────────────────────────────────────────────┘
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose

### 1. 启动基础设施

```bash
# 启动 MySQL + Redis + Milvus + etcd + MinIO + MCP WebSearch
docker-compose up -d
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入实际的 API Key：
#   DASHSCOPE_API_KEY        — DashScope API 密钥
#   EMBEDDING_API_KEY        — Embedding 服务密钥
#   TRANSCRIPTION_API_KEY    — 录音转写服务密钥
#   JWT_SECRET               — JWT 签名密钥
```

### 3. 启动后端

```bash
# 开发环境（H2 数据库，无需 MySQL）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 生产环境（需要 MySQL + Redis）
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 4. 启动前端

```bash
cd web
npm install
npx vite --host
```

前端默认运行在 `http://localhost:5173`

## 项目结构

```
src/main/java/com/tutorial/offerpilot/
├── OfferPilotApplication.java      # 启动类
├── common/                          # 公共基础类 (BaseEntity, ApiResponse)
├── enums/                           # 枚举 (UserRole, Visibility, DocumentStatus...)
├── config/                          # Spring 配置 (Security, Milvus, Redis...)
├── security/                        # JWT 认证鉴权 (JwtTokenProvider, Filter)
├── controller/                      # RESTful Controller (8 个)
├── service/                         # 业务逻辑层
│   └── ingestion/                   # 异步入库管道 (文档解析/分块/Embedding)
├── agent/                           # AgentScope 集成层
│   ├── AgentFactory.java           # Agent 构建 + Caffeine 池管理
│   ├── tool/                       # @Tool 工具类 (10 个本地工具)
│   └── middleware/                 # 中间件 (TokenMonitor, CostControl)
├── entity/                          # JPA 实体 (17 张表)
├── repository/                      # Spring Data JPA Repository
├── dto/                             # 请求/响应 DTO
├── converter/                       # Entity ↔ DTO 转换
└── exception/                       # 异常体系 (BusinessException + GlobalExceptionHandler)

src/main/resources/
└── application.yml                  # 主配置 (含多环境 Profile)
```

## Agent 工具清单

| 工具 | 名称 | 用途 |
|------|------|------|
| `parse_resume` | 简历解析 | 解析 PDF 简历，提取结构化信息 |
| `evaluate_resume` | 简历评估 | 多维度评估简历质量并给出改进建议 |
| `generate_next_question` | 模拟面试出题 | 获取出题指导，由 LLM 生成个性化面试题 |
| `analyze_answer` | 回答分析 | 分析面试回答质量，三维度评分 |
| `transcribe_audio` | 音频转写 | 将面试录音转为带时间戳的文字 |
| `search_questions` | 面试题检索 | 语义检索面试题库 |
| `search_answers` | 答案检索 | 检索优秀答案对标 |
| `smart_search` | 智能检索 | 自动识别意图，路由到对应知识库 |
| `search_resources` | 学习资源检索 | 检索学习文章/视频/代码示例 |
| `list_knowledge_bases` | 知识库列表 | 列出可访问的知识库及文档统计 |
| `search` (MCP) | 联网搜索 | 百度搜索兜底，知识库无结果时触发 |

## API 概览

| 路径 | 方法 | 说明 |
|------|------|------|
| `/api/v1/auth/register` | POST | 用户注册 |
| `/api/v1/auth/login` | POST | 用户登录 |
| `/api/v1/auth/logout` | POST | 用户登出 |
| `/api/v1/offerpilot/chat/**` | GET/POST | 对话 (SSE 流式 + 同步) |
| `/api/v1/offerpilot/upload` | POST | 上传简历/录音 |
| `/api/v1/offerpilot/reports/**` | GET | 查看分析报告 |
| `/api/v1/kb` | GET/POST/DELETE | 知识库管理 |
| `/api/v1/admin/models/**` | GET/POST | 模型配置管理 (管理员) |

## 关键设计

- **单 Agent 全能架构**：HarnessAgent 直接持有全部工具，按 SysPrompt 中定义的意图识别流程自主调用
- **多 Provider 切换**：用户私有 > 用户默认 > 全局默认 > application.yml 四级优先级
- **多租户知识库**：管理员创建公共库 + 用户创建私有库，Agent 检索时自动联合搜索
- **工具与 LLM 职责分离**：Tool 返回结构化 POJO，LLM 负责自然语言生成
- **联网兜底**：知识库无结果或相关性不足时，SysPrompt 确定性规则触发 MCP 联网搜索
- **JWT 黑名单**：Token 携带 jti 唯一标识，登出后写入黑名单表，定时清理过期记录

## 数据库

- 库名：`offerpilot`
- 17 张主表：op_user, kb_knowledge_base, kb_document, kb_chunk, interview_session, chat_session, chat_message, user_memory, model_config, model_name, token_blacklist 等
- ORM：Spring Data JPA

## 文档

- [需求规格说明书](Documents/01-需求规格说明书.md)
- [系统架构设计说明书](Documents/02-系统架构设计说明书.md)
- [详细设计说明书](Documents/03-详细设计说明书.md)
- [实现与编码规范](Documents/04-实现与编码规范.md)
- [部署运维手册](Documents/05-部署运维手册.md)

## License

MIT
