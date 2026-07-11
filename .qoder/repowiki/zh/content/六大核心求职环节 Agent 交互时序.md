# 六大核心求职环节 Agent 交互时序

<cite>
**本文引用的文件**   
- [01-需求规格说明书.md](file://Documents/01-需求规格说明书.md)
- [02-系统架构设计说明书.md](file://Documents/02-系统架构设计说明书.md)
- [AgentFactory.java](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java)
- [InterviewMode.java](file://src/main/java/com/tutorial/offerpilot/enums/InterviewMode.java)
- [InterviewModeService.java](file://src/main/java/com/tutorial/offerpilot/service/InterviewModeService.java)
- [MockInterviewTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/MockInterviewTool.java)
- [ConfidenceTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/ConfidenceTool.java)
- [KnowledgeGapTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/KnowledgeGapTool.java)
- [PriorityRankTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/PriorityRankTool.java)
- [ResumeQualityTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/ResumeQualityTool.java)
- [StarCheckTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/StarCheckTool.java)
- [TimeAllocationTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/TimeAllocationTool.java)
- [LearningPlanService.java](file://src/main/java/com/tutorial/offerpilot/service/LearningPlanService.java)
- [WeeklyReportScheduler.java](file://src/main/java/com/tutorial/offerpilot/service/WeeklyReportScheduler.java)
- [SmartSearchTool.java](file://src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java)
- [tools.json](file://workspace/tools.json)
</cite>

## 更新摘要
**变更内容**   
- 面试模式架构重大重构：InterviewMode从TECHNICAL/BEHAVIORAL重命名为TECH_DEEP/BEHAVIOR/SYSTEM_DESIGN/PRESSURE语义化命名
- MockInterviewTool集成新的InterviewModeService策略服务，支持模式感知的阶段轮转和难度递进
- 新增六维分析工具套件：ConfidenceTool（自信度分析）、KnowledgeGapTool（知识盲区检测）、PriorityRankTool（薄弱点排序）、ResumeQualityTool（简历质量检查）、StarCheckTool（STAR法则检查）、TimeAllocationTool（时间分配分析）
- LearningPlanService实现个性化学习计划管理，支持任务完成标记和优先级刷新
- WeeklyReportScheduler实现自动化周报生成，每周日20:00自动生成学习报告
- SmartSearchTool提供统一智能搜索入口，支持意图分类和多路召回

## 目录
- 环节一：简历智能诊断
- 环节二：AI 模拟面试
- 环节三：面试录音分析
- 环节四：目标公司面试情报
- 环节五：学习计划
- 环节六：薪资谈判

## 环节一：简历智能诊断
> 绘制用户上传简历 → 主Agent → spawn resume_coach → 调用 parse_resume/evaluate_resume/search_questions → 整合结果返回 的 Mermaid 时序图
> 标注每个步骤的工具调用和 SysPrompt 指令

```mermaid
sequenceDiagram
participant U as "用户"
participant C as "主Agent(offerPilotCoach)"
participant RC as "子Agent : resume_coach"
participant T1 as "工具 : parse_resume"
participant T2 as "工具 : evaluate_resume"
participant T3 as "工具 : search_questions"
participant T4 as "工具 : check_resume_quality"
participant T5 as "工具 : check_star"
participant KB as "知识库服务(KnowledgeBaseService)"
U->>C : "上传简历并请求诊断"
C->>RC : "spawn resume_coach"
RC->>T1 : "parse_resume(pdf_url)"
T1-->>RC : "结构化简历信息"
RC->>T2 : "evaluate_resume(resume_text)"
T2-->>RC : "评估指导 + 原始内容"
RC->>T4 : "check_resume_quality(resume_text)"
T4-->>RC : "简历质量检查结果"
RC->>T5 : "check_star(resume_text)"
T5-->>RC : "STAR要素完整性分析"
RC->>T3 : "search_questions(keyword=岗位/技能)"
T3->>KB : "多租户联合检索"
KB-->>T3 : "Top-K 题目与标签"
T3-->>RC : "相关题目集合"
RC-->>C : "诊断要点(评分/建议由LLM生成)"
C-->>U : "整合后的简历诊断报告"
```

- 关键说明
  - 主 Agent 仅做调度，不直接执行业务工具；子 Agent resume_coach 负责编排解析、评估与题库检索。
  - 工具返回"结构化数据 + 指导文本"，自然语言评分与建议由 LLM 在对话中动态生成（SysPrompt 明确禁止直接回显指导文本）。
  - 题库检索走多租户知识库（公共库 + 用户私有库）联合搜索。
  - **新增增强功能**：resume_coach现支持简历质量检查和STAR法则完整性验证，提供更全面的简历评估。

**章节来源**
- [01-需求规格说明书.md:41-55](file://Documents/01-需求规格说明书.md#L41-L55)
- [02-系统架构设计说明书.md:125-226](file://Documents/02-系统架构设计说明书.md#L125-L226)
- [AgentFactory.java:318-332](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L318-L332)
- [ResumeQualityTool.java:32-67](file://src/main/java/com/tutorial/offerpilot/agent/tool/ResumeQualityTool.java#L32-L67)
- [StarCheckTool.java:29-54](file://src/main/java/com/tutorial/offerpilot/agent/tool/StarCheckTool.java#L29-L54)

## 环节二：AI 模拟面试
> 绘制用户发起模拟面试 → 主Agent → spawn mock_interviewer → 多轮 generate_next_question/analyze_answer/search_answers → 面试总结 的 Mermaid 时序图
> 标注面试模式选择（技术深挖/行为面试/系统设计/压力面试）和追问机制

```mermaid
sequenceDiagram
participant U as "用户"
participant C as "主Agent(offerPilotCoach)"
participant MI as "子Agent : mock_interviewer"
participant TQ as "工具 : generate_next_question"
participant TA as "工具 : analyze_answer"
participant TS as "工具 : search_answers"
participant TM as "工具 : analyze_confidence"
participant TG as "工具 : detect_knowledge_gaps"
participant TT as "工具 : analyze_time_allocation"
participant DB as "数据库(面试记录)"
participant IMS as "面试模式服务(InterviewModeService)"
U->>C : "发起模拟面试(指定模式/岗位)"
C->>MI : "spawn mock_interviewer"
loop 每轮问答
MI->>TQ : "generate_next_question(context, mode, resume_text)"
TQ->>IMS : "getPhaseSequence(mode)/determineDifficulty(mode)"
IMS-->>TQ : "阶段序列/难度标签"
TQ->>DB : "读取历史题目/均分/阶段"
DB-->>TQ : "上下文信息"
TQ-->>MI : "出题指导(不含题目文本)"
MI-->>U : "LLM基于指导生成面试题"
U-->>MI : "候选人回答"
MI->>TA : "analyze_answer(question, answer)"
TA->>DB : "持久化原始QA"
DB-->>TA : "已保存"
alt 需要深度分析
MI->>TM : "analyze_confidence(answer)"
TM-->>MI : "自信度评分+口头禅分析"
MI->>TG : "detect_knowledge_gaps(question, answer)"
TG-->>MI : "知识盲区检测结果"
MI->>TT : "analyze_time_allocation(answers_json)"
TT-->>MI : "时间分配评估"
end
MI-->>U : "本轮反馈与追问(如需)"
end
MI-->>C : "面试结束，输出总结"
C-->>U : "综合总结(技术/表达/覆盖度趋势)"
```

- 关键说明
  - **重大重构**：面试模式从TECHNICAL/BEHAVIORAL重命名为TECH_DEEP/BEHAVIOR/SYSTEM_DESIGN/PRESSURE，提供更语义化的模式标识。
  - **策略服务集成**：MockInterviewTool集成InterviewModeService，支持模式感知的阶段轮转和难度递进策略。
  - **六维分析增强**：新增自信度分析、知识盲区检测、时间分配分析等工具，提供更全面的面试评估。
  - 追问机制：当回答深度不足时，LLM 依据 analyze_answer 的指导进行追问或补充提问。
  - 工具职责分离：generate_next_question 仅产出"出题指导"，实际题目由 LLM 生成；analyze_answer 仅产出"评估指导"，评分与评语由 LLM 生成。

**章节来源**
- [01-需求规格说明书.md:70-83](file://Documents/01-需求规格说明书.md#L70-L83)
- [02-系统架构设计说明书.md:519-579](file://Documents/02-系统架构设计说明书.md#L519-579)
- [InterviewMode.java:6-11](file://src/main/java/com/tutorial/offerpilot/enums/InterviewMode.java#L6-L11)
- [InterviewModeService.java:58-88](file://src/main/java/com/tutorial/offerpilot/service/InterviewModeService.java#L58-L88)
- [MockInterviewTool.java:55-87](file://src/main/java/com/tutorial/offerpilot/agent/tool/MockInterviewTool.java#L55-L87)
- [ConfidenceTool.java:36-91](file://src/main/java/com/tutorial/offerpilot/agent/tool/ConfidenceTool.java#L36-L91)
- [KnowledgeGapTool.java:40-88](file://src/main/java/com/tutorial/offerpilot/agent/tool/KnowledgeGapTool.java#L40-L88)
- [TimeAllocationTool.java:37-110](file://src/main/java/com/tutorial/offerpilot/agent/tool/TimeAllocationTool.java#L37-L110)

## 环节三：面试录音分析
> 绘制用户上传录音 → 主Agent → transcribe_audio → 并行 spawn tech_evaluator + expression_evaluator → 分别分析每题 → 主Agent 整合报告 的 Mermaid 时序图
> 标注并行执行的技术评估和表达评估两个维度

```mermaid
sequenceDiagram
participant U as "用户"
participant C as "主Agent(offerPilotCoach)"
participant TE as "子Agent : tech_evaluator"
participant EE as "子Agent : expression_evaluator"
participant TR as "工具 : transcribe_audio"
participant QA as "工具 : analyze_answer"
participant AS as "工具 : search_answers"
participant CC as "工具 : analyze_confidence"
participant KG as "工具 : detect_knowledge_gaps"
participant TA as "工具 : analyze_time_allocation"
U->>C : "上传录音并请求分析"
C->>TR : "transcribe_audio(file_path)"
TR-->>C : "带时间戳的文字记录"
par 对每个问答对并行评估
C->>TE : "spawn tech_evaluator"
TE->>AS : "search_answers(question=问题主题)"
AS-->>TE : "优秀答案Top-K"
TE->>QA : "analyze_answer(question, answer)"
TE->>KG : "detect_knowledge_gaps(question, answer)"
KG-->>TE : "知识盲区检测结果"
QA-->>TE : "评估指导(由LLM生成技术维度评分)"
and
C->>EE : "spawn expression_evaluator"
EE->>CC : "analyze_confidence(answer)"
CC-->>EE : "自信度评分+口头禅密度"
EE->>TA : "analyze_time_allocation(answers_json)"
TA-->>EE : "时间分配评估"
EE->>QA : "analyze_answer(question, answer)"
QA-->>EE : "评估指导(由LLM生成表达维度评分)"
end
C-->>U : "整合多维评估报告(技术/表达/亮点/不足/建议)"
```

- 关键说明
  - **六维分析增强**：tech_evaluator侧重技术深度与知识覆盖，expression_evaluator侧重表达逻辑与结构，两者都集成了新的分析工具。
  - 并行策略：tech_evaluator 侧重技术深度与知识覆盖，expression_evaluator 侧重表达逻辑与结构。
  - 评估流程：先检索优秀答案作为参考，再调用 analyze_answer 获取评估指导，最终由 LLM 生成具体分数与评语。
  - **新增分析维度**：自信度分析、知识盲区检测、时间分配分析为面试评估提供更全面的数据支撑。

**章节来源**
- [01-需求规格说明书.md:84-101](file://Documents/01-需求规格说明书.md#L84-L101)
- [02-系统架构设计说明书.md:477-517](file://Documents/02-系统架构设计说明书.md#L477-L517)
- [ConfidenceTool.java:36-91](file://src/main/java/com/tutorial/offerpilot/agent/tool/ConfidenceTool.java#L36-L91)
- [KnowledgeGapTool.java:40-88](file://src/main/java/com/tutorial/offerpilot/agent/tool/KnowledgeGapTool.java#L40-L88)
- [TimeAllocationTool.java:37-110](file://src/main/java/com/tutorial/offerpilot/agent/tool/TimeAllocationTool.java#L37-L110)

## 环节四：目标公司面试情报
> 绘制用户输入公司+岗位 → 主Agent → spawn company_researcher → search_company_info + search_questions → 生成"面试情报卡" 的 Mermaid 时序图
> 标注多租户检索与 Fallback 联网搜索触发条件

```mermaid
sequenceDiagram
participant U as "用户"
participant C as "主Agent(offerPilotCoach)"
participant CR as "子Agent : company_researcher"
participant CI as "工具 : search_company_interviews"
participant QS as "工具 : search_questions"
participant SS as "工具 : smart_search"
participant KB as "知识库服务(KnowledgeBaseService)"
participant MCP as "MCP web_search(百度等)"
U->>C : "输入公司名+岗位，请求面经情报"
C->>CR : "spawn company_researcher"
CR->>SS : "smart_search(query=公司+岗位)"
SS->>KB : "统一智能检索(意图分类+多路召回)"
KB-->>SS : "合并搜索结果"
alt 智能搜索无结果
CR->>CI : "search_company_interviews(company_name)"
CI->>KB : "多租户检索(公共库+私有库)"
KB-->>CI : "公司面经/风格/高频考点"
CR->>QS : "search_questions(keyword=岗位/技术栈)"
QS->>KB : "多租户检索"
KB-->>QS : "Top-K 题目与标签"
end
alt 知识库无结果或相关性不足(total==0 或 relevanceScore<0.6)
CR->>MCP : "web_search(公司+岗位+面经)"
MCP-->>CR : "互联网面经摘要"
end
CR-->>C : "面试情报卡(流程/考点/风格/差距)"
C-->>U : "结构化情报 + 个性化备考建议"
```

- 关键说明
  - **统一搜索入口**：company_researcher现优先使用smart_search工具进行统一智能检索，支持意图分类和多路召回。
  - 多租户检索：自动聚合公共库与用户私有库结果。
  - Fallback 规则：当 total==0 或最高相关性低于阈值时，SysPrompt 明确要求调用 MCP web_search 补充信息。
  - 子Agent工具白名单：包含 search_company_interviews、search_questions、smart_search、web_search 四个工具。

**章节来源**
- [01-需求规格说明书.md:56-69](file://Documents/01-需求规格说明书.md#L56-L69)
- [02-系统架构设计说明书.md:358-413](file://Documents/02-系统架构设计说明书.md#L358-413)
- [SmartSearchTool.java:39-157](file://src/main/java/com/tutorial/offerpilot/agent/tool/SmartSearchTool.java#L39-L157)
- [AgentFactory.java:389-399](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L389-L399)

## 环节五：学习计划
> 绘制用户请求学习计划 → 主Agent → spawn study_planner → track_progress + search_learning_resources + search_questions → 生成周计划与自测题 的 Mermaid 时序图
> 标注薄弱点优先级排序与资源推荐

```mermaid
sequenceDiagram
participant U as "用户"
participant C as "主Agent(offerPilotCoach)"
participant SP as "子Agent : study_planner"
participant TP as "工具 : track_progress"
participant RS as "工具 : search_resources"
participant QS as "工具 : search_questions"
participant PR as "工具 : prioritize_weaknesses"
participant LP as "服务 : LearningPlanService"
participant WR as "服务 : WeeklyReportScheduler"
participant DB as "数据库(进度/计划/掌握度)"
U->>C : "请求个性化学习计划"
C->>SP : "spawn study_planner"
SP->>TP : "track_progress(user_id)"
TP->>DB : "读取面试次数/知识点掌握度/任务完成度"
DB-->>TP : "结构化进度数据"
SP->>PR : "prioritize_weaknesses(user_id)"
PR->>LP : "calculatePriorities(user_id)"
LP->>DB : "查询掌握度数据"
DB-->>LP : "知识点掌握度列表"
LP-->>PR : "优先级排序结果"
PR-->>SP : "薄弱点优先级列表"
SP->>RS : "search_resources(topic=薄弱知识点)"
RS-->>SP : "教程/视频/代码示例"
SP->>QS : "search_questions(keyword=薄弱点)"
QS-->>SP : "自测题Top-K"
alt 知识库学习资源不足
SP->>MCP : "web_search(学习资源)"
MCP-->>SP : "互联网学习材料"
end
SP-->>C : "按优先级排序的学习计划(含资源与自测)"
C-->>U : "周计划 + 自测题 + 资源清单"
Note over WR : 每周日20 : 00自动生成学习周报
WR->>DB : "汇总本周学习数据"
DB-->>WR : "面试次数/任务完成/掌握度变化"
WR-->>WR : "生成分析报告"
```

- 关键说明
  - **优先级策略**：高频考点 × 低掌握度优先，通过PriorityRankTool和LearningPlanService实现量化排序。
  - **自动化周报**：WeeklyReportScheduler每周日20:00自动生成学习周报，汇总面试次数、任务完成情况、掌握度变化等维度。
  - 工具职责分离：track_progress 提供结构化数据与汇总指导，学习总结与任务拆解由 LLM 生成。
  - 子Agent工具白名单：包含 track_progress、prioritize_weaknesses、search_resources、search_questions、web_search 五个工具。
  - **增强功能**：新增薄弱点优先级排序和学习计划自动刷新机制，支持动态添加新发现的薄弱点任务。

**章节来源**
- [01-需求规格说明书.md:103-116](file://Documents/01-需求规格说明书.md#L103-L116)
- [02-系统架构设计说明书.md:580-657](file://Documents/02-系统架构设计说明书.md#L580-657)
- [PriorityRankTool.java:32-73](file://src/main/java/com/tutorial/offerpilot/agent/tool/PriorityRankTool.java#L32-L73)
- [LearningPlanService.java:84-144](file://src/main/java/com/tutorial/offerpilot/service/LearningPlanService.java#L84-L144)
- [WeeklyReportScheduler.java:43-72](file://src/main/java/com/tutorial/offerpilot/service/WeeklyReportScheduler.java#L43-L72)
- [AgentFactory.java:399-415](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L399-L415)

## 环节六：薪资谈判
> 绘制用户输入多个 offer → 主Agent → spawn salary_advisor → search_salary_data + compare_offers + generate_negotiation_script → 输出对比分析与话术 的 Mermaid 时序图
> 标注多维度对比与谈判风格配置

```mermaid
sequenceDiagram
participant U as "用户"
participant C as "主Agent(offerPilotCoach)"
participant SA as "子Agent : salary_advisor"
participant SS as "工具 : search_salary"
participant CO as "工具 : compare_offers"
participant GN as "工具 : generate_negotiation_script"
participant KB as "知识库服务(KnowledgeBaseService)"
participant MCP as "MCP web_search(联网搜索)"
U->>C : "提交多个 offer 详情，请求对比与谈判建议"
C->>SA : "spawn salary_advisor"
par 并行查询各公司薪资
SA->>SS : "search_salary(company, position)"
SS->>KB : "薪资数据检索"
KB-->>SS : "薪资范围/奖金/股票等"
SS-->>SA : "结构化薪资条目"
end
alt 本地薪资数据库无记录
SA->>MCP : "web_search(公司+岗位+薪资行情)"
MCP-->>SA : "最新薪资市场数据"
end
SA->>CO : "compare_offers(offers_json)"
CO-->>SA : "总包/优势劣势/成长评分"
SA->>GN : "generate_negotiation_script(current_offer, target, style)"
GN-->>SA : "开场白/论点/反驳应对/结束语"
SA-->>C : "对比结论与谈判策略"
C-->>U : "Offer 对比报告 + 定制话术"
```

- 关键说明
  - **增强功能**：salary_advisor子Agent现支持三个核心工具：search_salary、compare_offers、generate_negotiation_script。
  - **多维度对比**：compare_offers工具支持base、总包、股票、福利、通勤成本、技术成长等多维度分析。
  - **谈判风格配置**：generate_negotiation_script支持assertive（强硬）、moderate（温和）、conservative（保守）三种谈判风格。
  - **JSON适配模式**：compare_offers采用JSON字符串输入，通过ObjectMapper解析为OfferCompareRequest对象。
  - **简单委托模式**：generate_negotiation_script直接透传参数给SalaryService处理。
  - **Fallback机制**：当本地薪资数据库无记录时，自动调用web_search从互联网搜索最新薪资行情。
  - **子Agent工具白名单**：包含search_salary、compare_offers、generate_negotiation_script、web_search四个工具。

**章节来源**
- [01-需求规格说明书.md:118-129](file://Documents/01-需求规格说明书.md#L118-L129)
- [02-系统架构设计说明书.md:659-765](file://Documents/02-系统架构设计说明书.md#L659-L765)
- [AgentFactory.java:415-431](file://src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java#L415-L431)
- [tools.json:1-12](file://workspace/tools.json#L1-L12)