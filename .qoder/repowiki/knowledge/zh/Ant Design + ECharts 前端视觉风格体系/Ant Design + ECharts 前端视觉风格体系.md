---
kind: frontend_style
name: Ant Design + ECharts 前端视觉风格体系
category: frontend_style
scope:
    - '**'
source_files:
    - web/package.json
    - web/vite.config.ts
    - web/index.html
    - web/src/app/App.tsx
    - web/src/ui/pages/chat/ChatPage.tsx
    - web/src/ui/components/ScoreRadarChart.tsx
    - web/src/ui/components/MarkdownRenderer.tsx
    - web/src/ui/components/FileUploader.tsx
---

## 1. 系统/方案概览
- 技术栈：Vite + React 19 + TypeScript，UI 组件库采用 Ant Design 6（antd），图标来自 @ant-design/icons，图表使用 ECharts 6（echarts-for-react）。
- 样式策略：以 Ant Design 的 ConfigProvider 全局主题为中心，通过 token 配置统一主色、圆角等设计变量；页面布局大量使用 antd Layout 组件与内联 style，未引入 CSS Modules、SCSS/Tailwind 或 styled-components 等外部样式方案。
- 国际化：通过 antd/locale/zh_CN 将全局文案与日期格式化切换为中文。
- 构建与开发：Vite 提供 dev server 与代理（/api → http://localhost:8080），TypeScript 编译后由 Vite 打包。无独立 CSS 入口文件，样式全部在 JSX 中声明或通过 antd 内置样式生效。

## 2. 关键文件与包
- web/package.json：定义 antd、@ant-design/icons、echarts、echarts-for-react、zustand、react-router-dom 等依赖。
- web/vite.config.ts：React 插件、路径别名 `@` → src、开发代理 /api。
- web/index.html：应用入口 HTML，挂载 #root，语言 zh-CN。
- web/src/app/App.tsx：根组件，集中配置 antd ConfigProvider（locale=zh_CN、theme.token.colorPrimary='#1677ff'、borderRadius=6），并包裹 AntApp + BrowserRouter + 路由守卫。
- web/src/ui/pages/chat/ChatPage.tsx：典型页面，使用 antd Layout/Sider/Header/Content/Menu/Input/Button/Space 组合出侧边导航+顶部栏+消息区+输入区的整体布局。
- web/src/ui/components/ScoreRadarChart.tsx：ECharts 雷达图封装，颜色与主色 #1677ff 保持一致。
- web/src/ui/components/MarkdownRenderer.tsx：通过 className="markdown-body" 渲染 Markdown，行高与字号通过内联 style 控制。
- web/src/ui/components/FileUploader.tsx：复用 antd Upload 的默认样式类名（如 ant-upload-drag-icon）进行定制。

## 3. 架构与约定
- 主题来源单一：所有品牌色、圆角等视觉变量集中在 App.tsx 的 ConfigProvider.theme.token 中，组件不自行定义主题色，确保全仓一致。
- 布局范式固定：页面普遍遵循“左侧 Sider 功能菜单 + 顶部 Header + 中间 Content”三段式结构，间距与边框通过 antd 默认样式配合少量内联 style 微调。
- 图标规范：统一从 @ant-design/icons 按需导入具体图标，避免手写 SVG 或第三方图标库。
- 图表风格：ECharts 系列图表的 lineStyle/itemStyle/areaStyle 均引用主色 #1677ff，保持数据可视化与 UI 主色调一致。
- 状态与视图分离：Zustand store 仅管理业务状态，不持有任何样式逻辑；样式职责完全落在组件层。
- 响应式策略：当前未见媒体查询或栅格断点配置，主要依赖 antd 组件自身响应式能力与百分比宽度，尚未形成统一的响应式规范。

## 4. 开发者应遵守的规则
- 主题定制入口唯一：新增或修改品牌色、圆角、字体等全局视觉变量时，只允许在 App.tsx 的 ConfigProvider.theme.token 中调整，禁止在组件内硬编码 colorPrimary 等主题值。
- 优先使用 antd 组件：布局用 Layout/Sider/Header/Content，表单用 Form/Input/Select/Upload，反馈用 message/modal，避免手写原生 div 实现通用交互。
- 图标来源统一：一律从 @ant-design/icons 导入，不要引入其他图标库或手写 SVG。
- 图表配色约束：ECharts 配置中的 lineStyle/itemStyle/areaStyle 颜色必须使用主色 #1677ff 或其派生透明度，不得随意引入新色板。
- 样式组织方式：当前仓库未启用 CSS 文件，如需新增样式请尽量通过 antd 提供的 style/injectGlobal 机制或扩展 ConfigProvider theme；若确需自定义 CSS，应在对应模块目录内新建 .css 并通过 import 引入，避免散落在全局。
- 内联 style 的使用边界：仅用于局部微调（如 margin/padding/fontSize），不可替代 antd 组件属性（如 size/type/layout）来实现样式差异。
- 国际化：所有用户可见文案通过 antd locale 或 i18n 方案统一管理，不在 JSX 中混写英文字符串。