---
kind: frontend_style
name: Ant Design 主题 + 内联样式的前端视觉体系
category: frontend_style
scope:
    - '**'
source_files:
    - web/package.json
    - web/vite.config.ts
    - web/src/app/App.tsx
    - web/src/ui/components/ChatBubble.tsx
    - web/src/ui/components/MarkdownRenderer.tsx
---

## 1. 使用的系统/方法
- **UI 组件库**：Ant Design v6（`antd`）+ `@ant-design/icons`，作为全局 UI 基础。
- **主题方案**：通过根节点 `<ConfigProvider theme={{ token: { colorPrimary, borderRadius } }}>` 集中注入 Ant Design v6 的 CSS-in-JS 主题变量，未使用外部 CSS 文件、Tailwind、SCSS/Less 或 styled-components。
- **样式组织方式**：页面与组件基本采用 **React inline style** 和 Antd 组件内置 `style`/`className` 覆盖，无独立样式模块；Markdown 渲染通过 `react-markdown` + `rehype-highlight` 输出到带 `.markdown-body` 容器的 div。
- **构建工具链**：Vite + `@vitejs/plugin-react`，仅配置了路径别名 `@` → `src`，未引入 PostCSS/Tailwind/Sass 等预处理插件。

## 2. 关键文件与包
- `web/package.json`：声明 `antd`、`@ant-design/icons`、`react-markdown`、`echarts`、`zustand`、`axios` 等依赖。
- `web/vite.config.ts`：Vite 入口，仅含 React 插件、`@` 别名及开发代理，无样式相关插件。
- `web/src/app/App.tsx`：应用根组件，通过 `<ConfigProvider locale={zh_CN} theme={{ token: { colorPrimary: '#1677ff', borderRadius: 6 } }}>` 定义全局主色与圆角。
- `web/src/ui/components/ChatBubble.tsx`、`SessionList.tsx`、`PageHeader.tsx`、`ScoreRadarChart.tsx`：大量使用 inline style 控制布局与间距。
- `web/src/ui/components/MarkdownRenderer.tsx`：唯一用到 className 的地方，为 Markdown 内容提供 `.markdown-body` 容器。

## 3. 架构与约定
- **单一主题源**：所有 Antd 组件的主题来自 App 根节点的 `ConfigProvider.theme.token`，目前只覆盖了 `colorPrimary` 与 `borderRadius`，其余沿用 Antd 默认值。
- **无全局样式文件**：仓库中未发现任何 `.css` / `.scss` / `.less` 文件被 import，也没有 Tailwind 配置文件；样式以“就近 inline”为主，便于快速迭代但缺乏设计令牌抽象。
- **中文本地化**：通过 `antd/locale/zh_CN` 统一设置组件文案语言。
- **图表与富文本**：ECharts 用于雷达图等可视化，Markdown 渲染走 GFM + 代码高亮，不依赖额外主题。

## 4. 开发者应遵循的规则
1. **主题定制优先走 ConfigProvider**：新增颜色、字号、圆角等全局变量时，应在 App 根节点的 `theme.token` 中扩展，避免在组件里硬编码色值。
2. **尽量复用 Antd 组件的 style 属性**：对间距、尺寸等微调优先使用 Antd 组件内置 `style` 参数，减少手写 CSS class。
3. **谨慎新增全局样式文件**：当前项目没有建立统一的样式层，新增 `.css/.scss` 会破坏现有风格一致性，如需引入请先评估是否可通过 Antd token 解决。
4. **保持中文本地化一致**：新增页面/组件若涉及用户可见文案，应配合 `zh_CN` 本地化策略，避免中英文混排。
5. **Markdown 渲染容器类名固定**：`.markdown-body` 是现有 Markdown 渲染的唯一出口类名，后续样式覆盖应围绕该选择器进行。