---
kind: design
name: 通过 AgentScope Provider 映射统一 OpenAI 兼容厂商接入
source: session
category: adr
---

# 通过 AgentScope Provider 映射统一 OpenAI 兼容厂商接入

_来源：7fa0157 → 8095142 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

**状态：** accepted

## 背景
项目使用 AgentScope 作为 LLM 抽象层，但 deepseek、siliconflow、volcengine 等国内厂商的 SDK 与 OpenAI Java SDK 兼容。AgentFactory 在 resolveModel() 中直接以 provider 前缀拼接 modelId，若不进行映射会导致这些厂商无法被正确实例化。

## 决策驱动
- 屏蔽底层厂商差异
- 复用 AgentScope 已有 OpenAI 实现
- 最小化业务代码改动

## 备选方案
- **为每个厂商单独注册 AgentScope Extension 依赖并写独立分支逻辑** _（已否决）_ — 优点：语义清晰，各厂商完全解耦；缺点：每新增一个厂商都要改 AgentFactory + pom.xml，维护成本高
- **在 AgentFactory 中以白名单将 OpenAI 兼容厂商映射到 openai provider** — 优点：只需维护一份映射表，新增厂商零代码改动；缺点：若厂商后续偏离 OpenAI 协议需回退映射或加特判

## 决策
在 AgentFactory 中定义 OPENAI_COMPATIBLE_PROVIDERS 常量及 mapToAgentScopeProvider() 方法，将 deepseek/siliconflow/volcengine 的 modelId 前缀统一替换为 openai，再交由 AgentScope 的 OpenAI extension 处理；同时补齐 dashscope、anthropic、gemini、ollama 四个 extension 依赖以覆盖更多厂商。

## 影响
新增 OpenAI 兼容厂商时仅需在常量白名单中添加一行，无需修改工厂逻辑；但一旦某厂商 API 偏离 OpenAI 协议，映射策略会失效，需要引入更细粒度的适配层。