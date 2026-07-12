---
kind: frontend_style
name: Ant Design 主题与内联样式风格体系
category: frontend_style
scope:
    - '**'
source_files:
    - web/src/app/App.tsx
    - web/src/ui/pages/chat/ChatPage.tsx
    - web/src/ui/components/ChatBubble.tsx
    - web/src/ui/components/FileUploader.tsx
    - web/src/ui/components/MarkdownRenderer.tsx
    - web/src/ui/components/PageHeader.tsx
    - web/src/ui/components/ScoreRadarChart.tsx
    - web/package.json
    - web/vite.config.ts
---

## 1. 系统/方案概述
- 前端基于 React 19 + Vite 8，UI 组件库统一采用 Ant Design v6（antd），通过 ConfigProvider 在应用根节点集中配置主题、语言与全局行为。
- 样式策略以 Ant Design Token 覆盖 + 组件级 inline style 为主，未引入 CSS Modules、SCSS/Less、Tailwind 或 styled-components 等外部样式方案。
- 图表使用 ECharts + echarts-for-react，Markdown 渲染使用 react-markdown + remark-gfm + rehype-highlight，图标统一来自 @ant-design/icons。
- 构建工具链：Vite + @vitejs/plugin-react；代码规范由 oxlint 驱动，无 ESLint/Prettier。

## 2. 关键文件与包
- 主题入口：web/src/app/App.tsx（ConfigProvider + theme.token）
- 页面布局：web/src/ui/pages/chat/ChatPage.tsx（Layout/Sider/Header/Content 组合）
- 通用组件：web/src/ui/components/ChatBubble.tsx、FileUploader.tsx、MarkdownRenderer.tsx、PageHeader.tsx、ScoreRadarChart.tsx
- 依赖清单：web/package.json（antd、@ant-design/icons、echarts、zustand、react-router-dom 等）
- 构建配置：web/vite.config.ts（路径别名 @、开发代理 /api -> :8080）

## 3. 架构与约定
- 主题集中化：所有 Antd 组件共享同一套 ConfigProvider，在 App.tsx 中设置 locale={zh_CN} 与 theme.token.colorPrimary='#1677ff'、borderRadius: 6。业务色值（如 #1677ff）在多处硬编码复用，尚未抽离为独立 token 文件或常量。
- 布局结构：主框架采用 Antd Layout（Sider + Header + Content），侧边栏承载功能导航，顶部展示当前功能标题与用户操作。路由按功能域划分到 ui/pages/*，并通过 Guard 包裹受保护页面。
- 组件组织：ui/components 存放可复用 UI 片段（气泡、上传、Markdown 渲染、雷达图、页头）；ui/pages 按领域拆分页面。状态管理使用 Zustand（store/auth-store.ts、chat-store.ts、kb-store.ts），不混入 UI 逻辑。
- 样式实现方式：大量使用 React 的 style={{...}} 直接写内联样式（间距、背景、圆角、字号、Flex 布局等）。少量使用 className 对接 antd 内置类名（如 ant-upload-drag-icon、markdown-body）。未建立统一的 CSS 变量或 design tokens 文件，颜色/尺寸散落在各组件中。
- 国际化与文案：仅通过 ConfigProvider.locale = zh_CN 启用中文，业务提示文案全部硬编码在组件中（如 message.success('登录成功')）。

## 4. 开发者应遵循的规则
- 主题定制：新增全局视觉变量时，优先在 App.tsx 的 ConfigProvider.theme.token 中扩展，避免在各组件重复定义相同色值。保持 colorPrimary 与 borderRadius 的一致性。
- 样式写法：优先使用 Antd 组件自带属性（如 size、type、variant）控制外观；仅在无法通过 props 表达时才使用 style。将常用布局模式抽取为小组件，减少重复内联样式。
- 组件与页面分层：纯展示逻辑放入 ui/components，页面级布局与交互放在 ui/pages；跨页面复用的 UI 片段不得留在页面文件中。
- 图标与媒体资源：统一从 @ant-design/icons 引用，禁止自行引入 SVG 或图片作为图标；静态资源放 public/ 并通过绝对路径引用。
- 构建与路径：使用 @/ 别名导入 src 下模块，避免相对路径深层跳转；开发环境通过 Vite proxy 转发 /api 到后端 8080 端口。
- 代码质量：提交前运行 pnpm lint（oxlint），确保无语法/潜在错误；样式变更需保证在不同分辨率下布局不被破坏（当前未引入响应式断点，需注意移动端适配）。