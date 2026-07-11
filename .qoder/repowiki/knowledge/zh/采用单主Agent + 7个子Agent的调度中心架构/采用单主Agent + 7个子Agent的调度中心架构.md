---
kind: design
name: 采用单主Agent + 7个子Agent的调度中心架构
source: session
category: adr
---

# 采用单主Agent + 7个子Agent的调度中心架构

_来源：8095142 → 663851d 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

**状态：** accepted

## 背景
原有 HarnessAgent 直接持有全部 11 个 @Tool，职责过重且无法按领域隔离能力；需要支持简历、技术面、薪资等独立专业场景。

## 决策驱动
- 按领域拆分 Agent 职责
- 通过子 Agent 白名单限制工具访问范围
- 主 Agent 仅做任务分发不直接执行业务

## 备选方案
- **单 Agent 直连所有 Tool（现状）** _（已否决）_ — 优点：实现简单、无跨 Agent 通信开销；缺点：职责混杂、无法按领域隔离权限、扩展困难
- **1主Agent + 7子Agent 调度模式** — 优点：每个子 Agent 专注单一领域、可通过白名单精确控制工具可见性、主 Agent 统一编排复杂任务；缺点：需维护 SysPrompt 调度规则、spawn/resume 调用有额外延迟

## 决策
在 AgentFactory 中重写 buildSystemPrompt() 为调度中心版，定义 resume_coach / tech_evaluator / expression_evaluator / mock_interviewer / company_researcher / study_planner / salary_advisor 七个子 Agent，并通过 .subagent() 注册到主 Agent；主 Agent 严禁直接调用业务工具，只负责 spawn/resume 子 Agent 并整合结果。

## 影响
新增子 Agent 时只需在 AgentFactory 增加一个 buildXxxAgent() 方法并在 buildAgent() 中注册；但需同步维护 SysPrompt 中的分派指南与调度规则，且子 Agent 间协作必须经由主 Agent 中转。