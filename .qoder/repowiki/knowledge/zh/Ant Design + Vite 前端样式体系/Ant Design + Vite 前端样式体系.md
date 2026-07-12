---
kind: frontend_style
name: Ant Design + Vite 前端样式体系
category: frontend_style
scope:
    - '**'
source_files:
    - web/src/app/App.tsx
    - web/vite.config.ts
    - web/package.json
    - web/src/ui/pages/chat/ChatPage.tsx
    - web/src/ui/components/ChatBubble.tsx
    - web/src/ui/components/ScoreRadarChart.tsx
---

## 1. 系统/技术栈概览
- 框架与构建：React 19 + TypeScript + Vite 8，使用 `@vitejs/plugin-react`。
- UI 组件库：Ant Design v6（`antd`），配套图标包 `@ant-design/icons`。
- 状态管理：Zustand（轻量全局状态）。
- 路由：react-router-dom v6。
- 图表：ECharts 6 + echarts-for-react。
- Markdown 渲染：react-markdown + remark-gfm + rehype-highlight。
- HTTP：axios；SSE 流式读取：@microsoft/fetch-event-source。
- 无独立 CSS/SCSS/Less/Tailwind 文件，样式以 Ant Design 主题 + 内联 style 为主。

## 2. 关键文件与位置
- 应用入口与主题配置：`web/src/app/App.tsx`
- 开发代理与别名：`web/vite.config.ts`
- 页面示例（布局、菜单、消息气泡等）：
  - `web/src/ui/pages/chat/ChatPage.tsx`
  - `web/src/ui/components/ChatBubble.tsx`
  - `web/src/ui/components/PageHeader.tsx`
  - `web/src/ui/components/MarkdownRenderer.tsx`
  - `web/src/ui/components/ScoreRadarChart.tsx`
- 依赖清单：`web/package.json`

## 3. 架构与约定
- 主题集中化：通过 Ant Design 的 `<ConfigProvider>` 在根节点设置全局主题 token，当前仅覆盖主色 `colorPrimary: '#1677ff'` 与圆角 `borderRadius: 6`，并启用中文本地化 `zh_CN`。
- 布局结构：采用 Ant Design Layout（Sider + Header + Content）+ Menu 组合实现左侧功能导航 + 顶部栏 + 内容区的经典后台布局。
- 样式策略：
  - 优先使用 Ant Design 组件默认样式与 theme token，避免自定义 CSS。
  - 少量局部样式通过 JSX `style={{...}}` 内联注入（如间距、背景、边框、字号等）。
  - 图表颜色与主题保持一致（雷达图系列色使用 `#1677ff`，与主色一致）。
- 目录组织：`src/ui/pages/*` 按业务域划分页面，`src/ui/components/*` 存放可复用 UI 组件，`src/store/*` 为 Zustand store，`src/service/*` 为 API 服务层。
- 资源与构建：Vite 中配置了 `@` 路径别名指向 `./src`，开发服务器端口 3000，并通过 proxy 将 `/api` 转发至后端 8080。

## 4. 开发者应遵循的规则
- 主题与品牌色
  - 新增或调整全局视觉时，优先修改 `App.tsx` 中 `<ConfigProvider theme.token>` 下的 token，而不是在各处硬编码颜色值。
  - 保持主色统一为 `#1677ff`，如需扩展可在 theme token 中增加更多变量。
- 组件样式
  - 优先使用 Ant Design 组件 props 控制外观（如 `type`、`size`、`variant` 等），减少内联 style。
  - 仅在无法通过 props 表达时才使用内联 style，并保持命名空间清晰（如 padding/margin 使用固定单位，避免随意像素值）。
- 布局与响应式
  - 使用 Ant Design Grid（Row/Col）、Space、Layout 等内置布局能力，避免手写复杂 flex/grid 布局。
  - 移动端适配可通过 Ant Design 的断点工具类或媒体查询按需补充，但当前仓库未引入额外 CSS 框架。
- 图表与可视化
  - ECharts 配置中的颜色应与主题主色保持一致，便于整体风格统一。
- 代码规范与工程化
  - 使用 oxlint 进行静态检查（见 package.json scripts），提交前运行 `pnpm lint`。
  - 新页面/组件放在 `ui/pages` 或 `ui/components` 下，store 放入 `store`，API 调用放入 `service`。
- 国际化与文案
  - 所有用户可见文案使用中文，已在 ConfigProvider 中设置 zh_CN 本地化。

## 5. 结论
该前端采用“Ant Design 主题 + 内联 style”的轻量样式方案，没有独立的 CSS/SCSS/Tailwind 体系。视觉一致性由 App 根节点的 ConfigProvider 主题 token 驱动，配合 Ant Design 组件默认样式即可维持整体风格统一。后续如需更复杂的主题定制，建议在 theme.token 中扩展设计令牌，并逐步将内联样式迁移到 CSS-in-JS 或模块化样式文件中。