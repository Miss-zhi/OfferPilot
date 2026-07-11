---
kind: design
name: 通过 MCP workspace + tools.json 接入 web_search 联网搜索
source: session
category: adr
---

# 通过 MCP workspace + tools.json 接入 web_search 联网搜索

_来源：8095142 → 663851d 提交周期内记录的编码计划——内容为规划时意图，实现可能滞后或有出入。_

**状态：** accepted

## 背景
Agent 需要实时互联网信息能力，但现有 Toolkit 仅包含本地 Java 实现的 11 个工具；需要在运行时动态发现外部 MCP Server 提供的工具。

## 决策驱动
- 无需修改 Java 代码即可扩展新工具
- 利用 AgentScope 原生 workspace 扫描机制
- 使用社区 open-web-search-mcp 快速获得搜索能力

## 备选方案
- **在 Java 中硬编码 web_search 调用** _（已否决）_ — 优点：性能最好、类型安全；缺点：每次新增搜索源都要改代码重新编译部署
- **MCP workspace + tools.json 声明式配置** — 优点：零代码扩展、AgentScope 原生支持、可热加载；缺点：依赖 Node.js 环境运行 npx、工具名需在 Permission 中手动匹配

## 决策
创建 /opt/OfferPilot/workspace/tools.json，声明 open-web-search-mcp 作为 stdio 传输的 MCP Server，配置百度搜索引擎和 MAX_RESULTS=5；在 AgentFactory.buildAgent() 中通过 .workspace(Path.of("./workspace")) 启用扫描。

## 影响
部署环境必须安装 Node.js 16+ 以支持 npx；web_search 工具名需与 Permission 规则一致，否则会被拒绝；后续新增其他 MCP Server 只需追加 tools.json 条目无需改动 Java 代码。