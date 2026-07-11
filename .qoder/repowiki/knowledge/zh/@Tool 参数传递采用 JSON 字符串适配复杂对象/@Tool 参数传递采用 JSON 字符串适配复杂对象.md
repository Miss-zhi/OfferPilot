---
kind: design
name: '@Tool 参数传递采用 JSON 字符串适配复杂对象'
source: session
category: adr
---

# @Tool 参数传递采用 JSON 字符串适配复杂对象

_来源：8095142 → 663851d 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

**状态：** accepted

## 背景
compare_offers 需要传入 List<OfferItem> 嵌套结构，但 AgentScope 的 @Tool 注解不支持将 LLM 返回的复杂对象直接映射为 Java 集合参数。

## 决策驱动
- 保持 @Tool 方法签名简洁（基本类型参数）
- 复用已有的 OfferCompareRequest DTO
- 避免引入额外的序列化框架

## 备选方案
- **让 LLM 直接传结构化对象参数** _（已否决）_ — 优点：类型安全、无需手动解析；缺点：@Tool 注解不支持 List<T> 等泛型集合参数，AgentScope 会报错
- **LLM 传 JSON 字符串 + ObjectMapper 手动解析** — 优点：兼容 @Tool 基本类型约束、复用现有 DTO、解析失败可抛 BusinessException 明确错误；缺点：需要显式注入 ObjectMapper、JSON 格式错误由业务层处理

## 决策
在 SalaryTool.compare_offers 中接收 String json 参数，内部用注入的 ObjectMapper.readValue(json, OfferCompareRequest.class) 反序列化为请求对象，再委托给 salaryService.compareOffers()；解析失败抛出 BusinessException 以便 LLM 重试。

## 影响
compare_offers 的调用方（salary_advisor 子 Agent）必须以 JSON 字符串形式构造参数；generate_negotiation_script 因仅需 3 个基本类型参数，沿用简单委托模式无需 JSON 适配。