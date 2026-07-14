# 单Agent架构下核心求职环节交互流程

<cite>
**本文引用的文件**   
- [AgentFactory.java](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java)
- [MockInterviewTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/MockInterviewTool.java)
- [ResumeEvaluateTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/ResumeEvaluateTool.java)
- [SmartSearchTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java)
- [KnowledgeBaseService.java](file://src/main/java/com/tutorial/offerpilot/service/KnowledgeBaseService.java)
- [InterviewModeService.java](file://src/main/java/com/tutorial/offerpilot/service/InterviewModeService.java)
- [InterviewMode.java](file://src/main/java/com/tutorial/offerpilot/enums/InterviewMode.java)
</cite>

## 更新摘要
**变更内容**
- 架构重大重构：从多子Agent架构简化为单Agent直接调用工具模式
- 删除薪资谈判、学习计划等依赖已删除服务的环节描述
- 重写所有Mermaid时序图以反映新的单Agent架构
- 移除8个子Agent（resume_coach、tech_evaluator、expression_evaluator、mock_interviewer、company_researcher、study_planner、salary_advisor、knowledge_agent）
- 保留9个核心工具，由主Agent直接调用执行

## 目录
- 环节一：简历智能诊断
- 环节二：AI 模拟面试
- 环节三：面试录音分析
- 环节四：目标公司面试情报
- 环节五：知识检索与学习资源

## 环节一：简历智能诊断
> 绘制用户上传简历 → 单Agent → 直接调用 parse_resume/evaluate_resume → 整合结果返回 的 Mermaid 时序图
> 标注工具调用和系统提示词指令

```mermaid
sequenceDiagram
participant U as "用户"
participant A as "单Agent(offerPilotCoach)"
participant T1 as "工具 : parse_resume"
participant T2 as "工具 : evaluate_resume"
participant KB as "知识库服务(KnowledgeBaseService)"
U->>A : "上传简历并请求诊断"
A->>T1 : "parse_resume(pdf_url)"
T1-->>A : "结构化简历信息"
A->>T2 : "evaluate_resume(resume_text)"
T2-->>A : "评估指导 + 原始内容"
A->>KB : "基于简历关键词搜索相关题目"
KB-->>A : "Top-K 题目与标签"
A-->>U : "整合后的简历诊断报告"
```

- 关键说明
  - **架构简化**：单Agent直接执行业务工具，不再通过子Agent调度
  - 工具返回"结构化数据 + 指导文本"，自然语言评分与建议由 LLM 在对话中动态生成（SysPrompt 明确禁止直接回显指导文本）
  - 题库检索走多租户知识库（公共库 + 用户私有库）联合搜索
  - 权限控制：parse_resume、evaluate_resume 工具均配置为 ALLOW 权限

**章节来源**
- [AgentFactory.java:318-342](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L318-L342)
- [ResumeEvaluateTool.java:21-26](file://src/main/java/com/tutorial/offerpilot/agent/tool/ResumeEvaluateTool.java#L21-L26)

## 环节二：AI 模拟面试
> 绘制用户发起模拟面试 → 单Agent → 直接调用 generate_next_question/analyze_answer → 多轮问答 的 Mermaid 时序图
> 标注面试模式选择（技术深挖/行为面试/系统设计/压力面试）和追问机制

```mermaid
sequenceDiagram
participant U as "用户"
participant A as "单Agent(offerPilotCoach)"
participant TQ as "工具 : generate_next_question"
participant TA as "工具 : analyze_answer"
participant DB as "数据库(面试记录)"
participant IMS as "面试模式服务(InterviewModeService)"
U->>A : "发起模拟面试(指定模式/岗位)"
loop 每轮问答
A->>TQ : "generate_next_question(context, mode, resume_text)"
TQ->>IMS : "getPhaseSequence(mode)/determineDifficulty(mode)"
IMS-->>TQ : "阶段序列/难度标签"
TQ->>DB : "读取历史题目/均分/阶段"
DB-->>TQ : "上下文信息"
TQ-->>A : "出题指导(不含题目文本)"
A-->>U : "LLM基于指导生成面试题"
U-->>A : "候选人回答"
A->>TA : "analyze_answer(question, answer)"
TA->>DB : "持久化原始QA"
DB-->>TA : "已保存"
TA-->>A : "评估指导(由LLM生成评分评语)"
A-->>U : "本轮反馈与追问(如需)"
end
A-->>U : "综合总结(技术/表达/覆盖度趋势)"
```

- 关键说明
  - **架构简化**：单Agent直接调用 MockInterviewTool 和 AnswerAnalyzeTool
  - **策略服务集成**：MockInterviewTool集成InterviewModeService，支持模式感知的阶段轮转和难度递进策略
  - 追问机制：当回答深度不足时，LLM 依据 analyze_answer 的指导进行追问或补充提问
  - 工具职责分离：generate_next_question 仅产出"出题指导"，实际题目由 LLM 生成；analyze_answer 仅产出"评估指导"，评分与评语由 LLM 生成
  - 面试模式：TECH_DEEP/BEHAVIOR/SYSTEM_DESIGN/PRESSURE 四种模式

**章节来源**
- [MockInterviewTool.java:55-87](file://src/main/java/com/tutorial/offerpilot/agent/tool/MockInterviewTool.java#L55-L87)
- [InterviewModeService.java:18-32](file://src/main/java/com/tutorial/offerpilot/service/InterviewModeService.java#L18-L32)
- [InterviewMode.java:6-11](file://src/main/java/com/tutorial/offerpilot/enums/InterviewMode.java#L6-L11)

## 环节三：面试录音分析
> 绘制用户上传录音 → 单Agent → 直接调用 transcribe_audio/analyze_answer → 并行分析 → 整合报告 的 Mermaid 时序图
> 标注技术评估和表达评估两个维度

```mermaid
sequenceDiagram
participant U as "用户"
participant A as "单Agent(offerPilotCoach)"
participant TR as "工具 : transcribe_audio"
participant QA as "工具 : analyze_answer"
participant AS as "工具 : search_answers"
U->>A : "上传录音并请求分析"
A->>TR : "transcribe_audio(file_path)"
TR-->>A : "带时间戳的文字记录"
par 对每个问答对并行评估
A->>AS : "search_answers(question=问题主题)"
AS-->>A : "优秀答案Top-K"
A->>QA : "analyze_answer(question, answer)"
QA-->>A : "评估指导(由LLM生成技术维度评分)"
and
A->>QA : "analyze_answer(question, answer)"
QA-->>A : "评估指导(由LLM生成表达维度评分)"
end
A-->>U : "整合多维评估报告(技术/表达/亮点/不足/建议)"
```

- 关键说明
  - **架构简化**：单Agent直接调用 AudioTranscribeTool 和 AnswerAnalyzeTool
  - 并行策略：对同一录音中的不同问答对并行处理，提高分析效率
  - 评估流程：先检索优秀答案作为参考，再调用 analyze_answer 获取评估指导，最终由 LLM 生成具体分数与评语
  - 工具权限：transcribe_audio、analyze_answer、search_answers 均配置为 ALLOW 权限

**章节来源**
- [AgentFactory.java:326-333](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L326-L333)

## 环节四：目标公司面试情报
> 绘制用户输入公司+岗位 → 单Agent → 直接调用 smart_search/search_questions → 生成"面试情报卡" 的 Mermaid 时序图
> 标注统一智能检索与联网搜索兜底

```mermaid
sequenceDiagram
participant U as "用户"
participant A as "单Agent(offerPilotCoach)"
participant SS as "工具 : smart_search"
participant QS as "工具 : search_questions"
participant KB as "知识库服务(KnowledgeBaseService)"
participant MCP as "MCP web_search(百度等)"
U->>A : "输入公司名+岗位，请求面经情报"
A->>SS : "smart_search(query=公司+岗位)"
SS->>KB : "统一智能检索(意图分类+多路召回)"
KB-->>SS : "合并搜索结果"
alt 智能搜索无结果或相关性不足
A->>QS : "search_questions(keyword=岗位/技术栈)"
QS->>KB : "多租户检索"
KB-->>QS : "Top-K 题目与标签"
end
alt 知识库无结果或相关性不足(total==0 或 relevanceScore<0.6)
A->>MCP : "web_search(公司+岗位+面经)"
MCP-->>A : "互联网面经摘要"
end
A-->>U : "结构化情报 + 个性化备考建议"
```

- 关键说明
  - **统一搜索入口**：优先使用 smart_search 工具进行统一智能检索，支持意图分类和多路召回
  - 多租户检索：自动聚合公共库与用户私有库结果
  - Fallback 规则：当 total==0 或最高相关性低于阈值时，SysPrompt 明确要求调用 MCP web_search 补充信息
  - 工具白名单：包含 smart_search、search_questions、search 三个工具

**章节来源**
- [SmartSearchTool.java:35-153](file://src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java#L35-L153)
- [AgentFactory.java:334-339](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L334-L339)

## 环节五：知识检索与学习资源
> 绘制用户请求学习资源 → 单Agent → 直接调用 search_resources/smart_search → 推荐学习材料 的 Mermaid 时序图
> 标注资源类型识别与优先级排序

```mermaid
sequenceDiagram
participant U as "用户"
participant A as "单Agent(offerPilotCoach)"
participant RS as "工具 : search_resources"
participant SS as "工具 : smart_search"
participant KB as "知识库服务(KnowledgeBaseService)"
participant MCP as "MCP web_search(联网搜索)"
U->>A : "请求特定技能的学习资源"
A->>RS : "search_resources(topic=技能名称)"
RS->>KB : "学习资源检索"
KB-->>RS : "教程/视频/文档列表"
alt 本地资源不足
A->>SS : "smart_search(query=学习+技能)"
SS->>KB : "统一智能检索"
KB-->>SS : "扩展搜索结果"
end
alt 仍无足够结果
A->>MCP : "web_search(学习资源)"
MCP-->>A : "互联网学习材料"
end
A-->>U : "按优先级排序的学习计划(含资源与自测)"
```

- 关键说明
  - **架构简化**：单Agent直接调用 ResourceSearchTool 和 SmartSearchTool
  - 资源检索：优先使用专门的 search_resources 工具，失败时回退到统一的 smart_search
  - 联网兜底：当本地知识库资源不足时，自动调用 MCP web_search 获取互联网学习材料
  - 工具权限：search_resources、smart_search、search 均配置为 ALLOW 权限

**章节来源**
- [AgentFactory.java:332-339](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L332-L339)

## 架构变化对比

### 原有多Agent架构
```
用户 → 主Agent(调度中心) → spawn 子Agent → 子Agent调用工具 → 返回结果
```

### 现单Agent架构
```
用户 → 单Agent(全能助手) → 直接调用工具 → 返回结果
```

### 主要变更点
1. **移除子Agent调度**：不再使用 spawn/resume_subagent 机制
2. **工具直调模式**：主Agent直接拥有所有工具权限并调用
3. **简化权限控制**：所有工具均为 ALLOW 权限，无需细粒度控制
4. **统一系统提示词**：从"调度中心"角色改为"全能助手"角色

**章节来源**
- [AgentFactory.java:120-155](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L120-L155)
- [AgentFactory.java:212-309](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L212-L309)