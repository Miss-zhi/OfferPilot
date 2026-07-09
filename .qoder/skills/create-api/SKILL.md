---
name: create-api
description: 生成 API 层 RestController 代码，提供标准 RESTful CRUD 接口。当需要为某个实体新增 Controller 或 REST 端点时使用。
---

# create-api

## 目的
生成 API 层代码：RestController，提供标准 RESTful CRUD 接口。

## 前置规范
**必须先读取并严格遵循** `.qoder/rules/java-api-layer.mdc`

## 工作流
1. 确认实体名称
2. 确认对应的 Service
3. 生成 Controller + Request/Form 表单
4. 输出到 src/main/java/com/tutorial/offerpilot/controller/

## 输出文件

| 文件 | 路径 |
|------|------|
| Controller | `src/main/java/com/tutorial/offerpilot/controller/{Name}Controller.java` |
| CreateForm | `src/main/java/com/tutorial/offerpilot/dto/{Name}CreateForm.java` |
| UpdateForm | `src/main/java/com/tutorial/offerpilot/dto/{Name}UpdateForm.java` |

## 约束
- 所有 Controller 方法必须返回 `ApiResponse<T>`
- 请求参数使用 Form 表单对象（`@RequestBody`）
- Form 命名：`{Name}CreateForm` / `{Name}UpdateForm`
- 禁止将 Form 对象传递给 Service 层（拆解为基础类型再调用）
- 禁止 try-catch 捕获异常
- 禁止直接返回 Entity（必须用 ApiResponse 包装）
- 在 Controller 中创建 Entity 时，必须设置 `setCreateBy("SYSTEM")` / `setUpdateBy("SYSTEM")`
- 在 Controller 中更新 Entity 时，必须设置 `setUpdateBy("SYSTEM")`
- 必须包含版权注释
