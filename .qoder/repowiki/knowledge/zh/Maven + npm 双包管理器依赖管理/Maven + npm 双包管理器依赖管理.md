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

本仓库采用前后端分离架构，分别使用 Maven（后端 Java）与 npm（前端 React）进行第三方依赖管理。

## 后端：Maven（pom.xml）
- 继承 spring-boot-starter-parent:3.2.5，通过 properties 集中声明版本：agentscope.version=2.0.0-RC5、jjwt.version=0.12.5，统一 AgentScope 各扩展模块（core/harness/openai/dashscope/anthropic/gemini/ollama/studio）的版本号。
- 数据库驱动按环境拆分 scope：H2 为 runtime（开发），MySQL Connector/J 为 runtime（生产）。
- Lombok、Configuration Processor 标记为 optional，不随应用打包；JWT 的 impl/jackson 子模块也设为 runtime。
- 测试依赖（Spring Security Test、Testcontainers、REST Assured）全部限定 test scope。
- 构建插件：spring-boot-maven-plugin 排除 Lombok 参与打包，jacoco-maven-plugin 在 test 阶段生成覆盖率报告。
- 未配置私有 Maven 仓库或镜像，依赖直接拉取中央仓库。

## 前端：npm（web/package.json + package-lock.json）
- 使用 Vite 8 + TypeScript 6 作为构建工具链，React 19 + Ant Design 6 作为 UI 栈。
- 所有依赖以 ^ 语义化版本声明，锁定文件 package-lock.json（lockfileVersion 3）提交至仓库，确保团队与 CI 安装结果一致。
- 无自定义 registry 或 .npmrc，默认使用 npm 官方源。

## 约定与约束
- 新增后端依赖时优先放入 properties 统一管理版本号，避免散落在 dependencies 中重复声明。
- 仅编译期/可选依赖使用 optional，运行期依赖不得标记 optional。
- 新增前端依赖需同步更新 package-lock.json，禁止只改 package.json 不锁版本。
- 暂未引入私有制品库或代理缓存，若后续接入企业内网需补充 Maven settings.xml 与 npm registry 配置。