---
name: review-web
description: 审查 web 前端代码的分层依赖合规性、TypeScript 类型安全性和命名规范。当用户要求 review 前端代码、检查 web 层质量、或提交前端代码前使用。
---

# Review Web

对 `web/src/` 下的前端代码进行三维度审查。

## 审查流程

1. 读取目标文件（或整个 `web/src/` 目录）
2. 按以下三个维度逐项检查
3. 输出审查报告

---

## 维度 A：分层依赖合规

前端 4 层架构依赖方向：

```
app → ui/pages → service → infra
                → ui/components
```

### 检查项

| 规则 | 说明 |
|------|------|
| pages 不直接调 axios | 所有 HTTP 请求必须走 service 层 |
| service 只引用 infra | service 不能引用 ui、app 层 |
| service 不引用 UI 组件 | service 层保持与页面完全解耦 |
| infra 不依赖业务模块 | infra 层是纯基础设施工具 |
| pages 不跨层直接引用 infra | pages 应通过 service 间接使用 infra |
| app 层不放业务代码 | app 只负责路由配置和全局布局 |
| components 不含业务逻辑 | components 只放通用可复用组件 |

### 违规示例

```typescript
// ❌ pages 中直接使用 axios
import axios from 'axios';
const data = await axios.get('/api/users');

// ✅ 应通过 service 调用
import { userService } from '../service/userService';
const data = await userService.findAll();
```

---

## 维度 B：TypeScript 类型安全

### 检查项

| 规则 | 说明 |
|------|------|
| 禁止显式 any | 不允许 `: any` 类型标注 |
| 函数参数和返回值有类型 | 所有函数必须有明确的类型标注 |
| API 响应有类型定义 | service 层返回数据必须定义接口 |
| 组件 Props 有类型 | React 组件 Props 必须定义 interface 或 type |
| 避免 as 类型断言滥用 | 优先用类型守卫，减少 `as` 强转 |
| 事件处理器有类型 | onChange、onClick 等回调参数需标注事件类型 |

### 违规示例

```typescript
// ❌ 使用 any
const data: any = await userService.findAll();

// ✅ 定义明确类型
interface User {
  id: number;
  name: string;
  email: string;
}
const data: User[] = await userService.findAll();
```

---

## 维度 C：命名规范

### 检查项

| 规则 | 说明 |
|------|------|
| 文件名 kebab-case | `user-service.ts` 而非 `userService.ts` 或 `UserService.ts` |
| 组件文件 PascalCase | `UserPage.tsx`、`MemoryPage.tsx` |
| 接口/类型 PascalCase | `interface UserData {}` |
| 变量/函数 camelCase | `const userName = ...` |
| 常量 UPPER_SNAKE_CASE | `const MAX_RETRY = 3` |
| service 文件命名 | `{entity}Service.ts` 格式 |
| 页面文件命名 | `{Entity}Page.tsx` 格式 |

---

## 审查报告格式

```markdown
# Web 前端审查报告

## 审查范围
- 审查文件列表

## A. 分层依赖合规
- ✅ 通过 / ❌ 违规项（附文件路径 + 行号 + 说明）

## B. TypeScript 类型安全
- ✅ 通过 / ❌ 违规项（附文件路径 + 行号 + 说明）

## C. 命名规范
- ✅ 通过 / ❌ 违规项（附文件路径 + 行号 + 说明）

## 总结
- 通过项数 / 总检查项数
- 严重程度分级：🔴 必须修复 / 🟡 建议优化
```
