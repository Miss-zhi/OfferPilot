---
name: create-component
description: 创建新的 @Tool 工具类，封装 Agent 可调用的专项能力。当需要为 Agent 添加新工具时使用。
---

# create-component

## 目的
创建新的 Agent @Tool 工具类，包含工具方法 + Result POJO + 可选 Middleware。

## 前置规范
**必须先读取并严格遵循** `.qoder/rules/java-component-layer.mdc`

## 工作流
1. 确认工具名称和职责描述
2. 生成 @Tool 工具类（使用 @Component 注入）
3. 生成 Result POJO（工具返回值）
4. 可选：生成/更新 Middleware

## 输出文件

| 文件 | 路径 |
|------|------|
| Tool 类 | `src/main/java/com/tutorial/offerpilot/agent/tool/{Name}Tool.java` |
| Result POJO | `src/main/java/com/tutorial/offerpilot/dto/tool/{Name}Result.java` |

## 约束
- 工具类必须使用 `@Component` 注入 Spring 容器
- 工具方法必须使用 `@Tool` + `@ToolParam` 注解
- 工具返回值必须是 POJO（禁止返回 String JSON）
- Result POJO 的字段必须有清晰的业务含义
- 工具注入 Service 时通过构造器注入
- 必须包含版权注释
