---
kind: design
name: 从多 Agent 调度架构降级为单 Agent + 工具直调
source: session
category: adr
---

# 从多 Agent 调度架构降级为单 Agent + 工具直调

_来源：a219d10 → 1e2013b 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

**状态：** accepted

## 背景
系统运行成本过高，需要大幅削减 LLM 调用次数和子 Agent 开销。原有架构通过 AgentFactory 管理 8 个子 Agent（简历、技术评估、面试模拟、公司情报、学习计划、薪资谈判、知识检索等），每个任务触发一次子 Agent 调用，导致单次对话多次 LLM 请求。

## 决策驱动
- 降低 LLM 调用成本
- 减少响应延迟
- 简化系统复杂度

## 备选方案
- **保留多 Agent 架构但限制并发** _（已否决）_ — 优点：功能完整，职责清晰；缺点：仍需多次 LLM 调用，成本下降有限
- **单 Agent + 工具直调** — 优点：每次对话仅一次 LLM 调用；System Prompt 直接声明可用工具；无需子 Agent 注册/调度开销；缺点：Agent 需理解更多上下文；Prompt 变长；失去细粒度权限控制

## 决策
删除全部 8 个 buildXxxAgent() 方法和 SubagentDeclaration，重写 AgentFactory 的 System Prompt 为「全能助手」角色，在 Prompt 中直接列出保留的 9 个工具及其使用场景，由主 Agent 自行决定调用哪个工具。同时移除 CompanySearchTool、ProgressTrackTool、PriorityRankTool、SalaryTool 四个已废弃工具。

## 影响
LLM 调用次数从每轮对话 2-3 次降至 1 次，显著降低成本与延迟；但 Agent 的 System Prompt 更长且更复杂，对模型指令遵循能力要求更高；权限控制从细粒度 PermissionRule 退化为粗粒度的工具白名单。