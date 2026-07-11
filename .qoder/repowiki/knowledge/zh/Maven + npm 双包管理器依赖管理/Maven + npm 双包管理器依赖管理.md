---
kind: dependency_management
name: Maven + npm 双包管理器依赖管理
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/package-lock.json
---

本项目采用前后端分离架构，分别使用 Maven（后端）和 npm（前端）进行依赖声明与版本锁定。

**后端（Java/Spring Boot）**
- 构建工具：Maven，继承 spring-boot-starter-parent:3.2.5，统一 Spring 生态版本。
- 核心依赖通过 properties 集中声明版本号：agentscope.version=2.0.0-RC5、jjwt.version=0.12.5；其余第三方库直接内联指定版本。
- AgentScope v2 以多模块形式引入：agentscope-core、agentscope-harness 以及 OpenAI/DashScope/Anthropic/Gemini/Ollama 等模型扩展，全部对齐 ${agentscope.version}。
- 数据库驱动按环境拆分：H2 仅 runtime（开发），MySQL Connector/J 仅 runtime（生产）。
- Lombok 标记为 optional，并通过 spring-boot-maven-plugin 的 excludes 排除打包产物。
- 测试依赖（Testcontainers、REST Assured、Spring Security Test）均限定 scope=test。
- 未检出 .m2/repository 或 vendor/ 目录，依赖通过远程仓库下载，无私有镜像配置。

**前端（React/Vite）**
- 包管理器：npm，依赖声明在 web/package.json，精确锁定在 web/package-lock.json（lockfileVersion=3）。
- 运行时依赖包括 Ant Design 6、Axios、ECharts、Zustand、React Router DOM 等；开发依赖包含 Vite 8、TypeScript ~6.0.2、oxlint。
- 所有依赖使用 ^ 主版本范围，由 lockfile 固定实际解析版本。
- 未检出私有 registry 或 .npmrc 覆盖配置。

**约定与建议**
- 新增 Java 依赖时优先放入 properties 统一管理版本号，避免散落在各 dependency 中。
- 保持 AgentScope 相关依赖版本一致，随框架升级同步更新 ${agentscope.version}。
- 新增前端依赖后需提交 package-lock.json，确保 CI 可复现安装。
- 当前未发现私有仓库或代理配置，若引入企业内网包需补充 Maven settings.xml 与 npm .npmrc。