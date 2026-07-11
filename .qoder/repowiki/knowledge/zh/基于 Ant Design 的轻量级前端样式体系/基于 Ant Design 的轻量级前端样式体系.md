---
kind: frontend_style
name: 基于 Ant Design 的轻量级前端样式体系
category: frontend_style
scope:
    - '**'
source_files:
    - web/package.json
    - web/src/app/App.tsx
    - web/src/ui/pages/chat/ChatPage.tsx
    - web/src/ui/components/PageHeader.tsx
    - web/src/ui/components/MarkdownRenderer.tsx
    - web/src/ui/components/ScoreRadarChart.tsx
    - web/src/ui/components/ChatBubble.tsx
    - web/src/ui/components/FileUploader.tsx
---

## 1. 系统/方案概述
- 技术栈：React 19 + Vite + TypeScript，UI 组件库采用 Ant Design v6（含 @ant-design/icons），状态管理使用 Zustand。
- 样式策略：以 Ant Design 主题覆盖为主、内联 style 为辅，未引入 Tailwind、CSS Modules、SCSS/Less 等外部样式语言或原子化框架。
- 构建工具链：Vite + @vitejs/plugin-react，无独立 CSS 入口文件，样式随组件按需加载。

## 2. 关键文件与包
- 依赖声明：web/package.json（antd、@ant-design/icons、echarts-for-react、react-markdown 等）
- 全局主题配置：web/src/app/App.tsx（通过 ConfigProvider 设置 antd token）
- 页面布局示例：web/src/ui/pages/chat/ChatPage.tsx（Layout/Sider/Header/Content/Menu 组合）
- 通用 UI 组件：web/src/ui/components/PageHeader.tsx、MarkdownRenderer.tsx、ScoreRadarChart.tsx、ChatBubble.tsx、FileUploader.tsx、SseStreamRenderer.tsx
- Markdown 渲染：web/src/ui/components/MarkdownRenderer.tsx（依赖 remark-gfm + rehype-highlight，通过 className="markdown-body" 对接外部样式）

## 3. 架构与约定
- 主题集中化：在 App 根节点使用 ConfigProvider 注入全局 theme.token（如 colorPrimary='#1677ff'、borderRadius=6），并配置 zh_CN 本地化。所有 antd 组件默认继承该主题。
- 布局模式：以 antd Layout（Sider + Content + Header）+ Menu 作为标准后台布局骨架，各页面复用此结构；顶部 Header 显示当前功能标题与用户信息，左侧 Sider 提供导航菜单。
- 组件粒度：ui/components 存放可复用的原子/分子级组件（气泡消息、文件上传、雷达图、Markdown 渲染、SSE 流式渲染等），ui/pages 按业务域组织页面。
- 图标来源：统一从 @ant-design/icons 导入，避免自行维护 SVG。
- 图表渲染：通过 echarts-for-react 封装 Radar 等图表组件，尺寸通过内联 style 控制。
- Markdown 展示：使用 react-markdown + remark-gfm + rehype-highlight，并通过 className="markdown-body" 暴露给外部样式（仓库中未见对应 CSS 文件，实际样式可能由 antd/markdown 默认样式或运行时注入）。

## 4. 开发者应遵循的规则
- 优先使用 Ant Design 组件与 props 完成样式表达，仅在布局微调处使用内联 style，避免手写 CSS 类名。
- 新增全局视觉变量时，应在 App.tsx 的 ConfigProvider.theme.token 中扩展，而非在各组件中硬编码颜色/圆角值。
- 页面布局统一采用 Layout + Menu 组合，保持侧边栏宽度、Header 高度、内容区 padding 的一致性。
- 图标一律从 @ant-design/icons 引入，不要自行实现 SVG 图标。
- 如需自定义 Markdown 外观，通过修改 className="markdown-body" 的样式或扩展 rehype/remark 插件实现，不要在 JSX 中堆叠大量行内样式。
- 图表组件的尺寸通过 props 传入，避免在组件内部写死宽高；若需响应式，可在父容器层处理。
- 不引入新的 CSS-in-JS 或样式预处理框架，保持现有 antd 主题 + 少量内联 style 的轻量风格一致。