---
name: create-web-page
description: 生成前端 CRUD 页面组件，包含 Service 层（API 调用）、UI 层（查询/表格/分页/操作）和路由注册。当需要为某个实体新增前端管理页面时使用。
---

# create-web-page

## 目的
生成前端完整 CRUD 页面：Service 层 + UI 层（查询栏/操作栏/表格/分页/Modal 表单）+ App 层路由注册。

## 前置规范
**必须先读取并严格遵循**以下规则文件：

1. `.qoder/rules/web-page-layer.mdc` — 页面层组件规范（入口约定、响应解包、CRUD 布局、状态变量、表格/表单）
2. `.qoder/rules/web-layer.mdc` — 前端 4 层架构规范（目录结构、分层职责、依赖规则）
3. `.qoder/rules/react-typescript.mdc` — TypeScript 类型安全规范（禁止 any）

## 工作流

```
确认实体信息
    ↓
生成 Service 层（API 调用 + 类型定义）
    ↓
生成 UI 页面组件（CRUD 完整页面）
    ↓
更新 App.tsx 路由注册
```

### 步骤说明

1. **确认实体信息**
   - 实体名称（如 `Memory`、`User`）
   - 业务字段列表（字段名、类型、标签）
   - API RESTful 路径（如 `memory-items`）
   - 查询条件字段

2. **生成 Service 层**
   - 类型定义（`{Entity}Item` interface）
   - CRUD 方法（`list` / `getById` / `create` / `update` / `delete`）

3. **生成 UI 页面组件**
   - 查询栏（搜索条件 + 查询/重置）
   - 操作栏（新增 + 批量删除）
   - 表格（列定义 + 行选择 + 操作列 + 分页）
   - Modal 表单（新增/编辑弹窗）

4. **更新路由**
   - `App.tsx` 中添加路由 `<Route path="/{path}" element={<{Entity}Page />} />`
   - 左侧菜单添加菜单项

## 输出文件

| 文件 | 路径 |
|------|------|
| Service | `web/src/service/{entity}Service.ts` |
| Page | `web/src/ui/pages/{Entity}Page.tsx` |
| 路由更新 | `web/src/app/App.tsx`（追加 Route + MenuItem） |

## 约束

- Service 层 `res.data` 已由 `infra/http.ts` 拦截器解包，**禁止重复 `.data`**
- 页面组件必须包含查询栏、操作栏、表格、分页四个区域
- 新增/编辑使用 Modal 弹窗，**禁止**使用独立页面
- 删除必须使用 `<Popconfirm>` 二次确认
- 所有操作完成后必须重新加载数据（`loadData()`）
- 必须包含版权注释
- 禁止使用 `any` 类型
- 接口路径使用 RESTful 风格：`/api/{resources}`
