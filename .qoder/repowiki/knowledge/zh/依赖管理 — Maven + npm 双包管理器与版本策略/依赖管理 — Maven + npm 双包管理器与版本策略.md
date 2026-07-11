---
kind: dependency_management
name: 依赖管理 — Maven + npm 双包管理器与版本策略
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/package-lock.json
---

## 1. 使用的系统/方法
- **后端（Java）**：使用 Maven，以 `spring-boot-starter-parent:3.2.5` 作为父 POM，继承 Spring Boot BOM 统一管理 Spring 生态依赖版本。
- **前端（React/Vite）**：使用 npm，通过 `package.json` + `package-lock.json` 锁定依赖树。
- **无私有仓库/镜像配置**：代码库中未发现 `.m2/settings.xml`、`~/.m2/settings.xml`、Nexus/Sonatype/Aliyun 镜像或 `GOPRIVATE` 等私有源配置，默认走中央仓库与 npmjs.org。
- **无 vendoring / lockfile 提交策略差异**：Maven 未提交 `pom.xml.lock`；npm 已提交 `web/package-lock.json`，用于保证前端构建可复现。

## 2. 关键文件
- `pom.xml` — 后端所有依赖声明、版本属性、插件（spring-boot-maven-plugin、jacoco）集中定义。
- `web/package.json` — 前端运行时与开发期依赖清单。
- `web/package-lock.json` — 前端完整依赖树与校验和锁定文件。
- `src/main/resources/application.yml` — 运行时 LLM Provider 地址（DashScope 等），属于“外部服务依赖”的配置而非包依赖。

## 3. 架构与约定
- **Spring Boot BOM 统一版本**：通过 `<parent>` 引入 `spring-boot-starter-parent`，Spring 相关 starter（web、security、data-jpa、data-redis、validation、test 等）不显式写 version，由 BOM 管控。
- **核心第三方依赖集中声明在根 POM**：
  - AgentScope Java v2：`agentscope-core`、`agentscope-harness` 及多个 model extension（openai/dashscope/anthropic/gemini/ollama）统一使用 `${agentscope.version}` 属性（当前为 `2.0.0-RC5`）。
  - JWT：`jjwt-api/impl/jackson` 三件套统一使用 `${jjwt.version}`（`0.12.5`）。
  - 向量数据库：`io.milvus:milvus-sdk-java:2.4.1`。
  - HTTP：`com.squareup.okhttp3:okhttp:4.12.0`（用于 DashScope Embedding API）。
  - 文档解析：`org.apache.pdfbox:pdfbox:3.0.2`、`org.apache.poi:poi-ooxml:5.2.5`。
  - JSON：`com.google.code.gson:gson`（由 Spring Boot BOM 提供版本）。
  - 缓存：`com.github.ben-manes.caffeine:caffeine`（BOM 管理版本）。
  - 日志：`net.logstash.logback:logstash-logback-encoder:7.4` + `org.codehaus.janino:janino:3.1.12`。
- **作用域与可选依赖**：
  - Lombok、configuration-processor 标记 `<optional>true</optional>`，不参与打包产物。
  - H2、mysql-connector-j 标记 `<scope>runtime</scope>`，仅运行期生效。
  - 测试依赖（spring-security-test、testcontainers、rest-assured）均标记 `<scope>test</scope>`。
- **构建插件**：
  - `spring-boot-maven-plugin` 排除 lombok 避免打入 jar。
  - `jacoco-maven-plugin:0.8.12` 集成覆盖率收集与报告生成。
- **前端依赖管理**：
  - React 19 + Ant Design 6 + ECharts 6 + Zustand 5，全部使用 `^` 语义化版本范围。
  - 通过 `package-lock.json`（lockfileVersion=3）锁定精确子依赖版本与 integrity hash，确保 CI/本地构建一致。
  - 构建工具链（vite、typescript、oxlint、@vitejs/plugin-react）集中在 devDependencies。

## 4. 开发者应遵循的规则
- **新增后端依赖**：
  - 统一在根 `pom.xml` 的 `<dependencies>` 中添加，优先复用 Spring Boot BOM 提供的版本；若需固定版本，先在 `<properties>` 中声明变量（如 `${agentscope.version}`），再在依赖处引用。
  - 明确标注 `<scope>`（runtime/test/compile）与 `<optional>`，避免污染生产包体积。
  - 不要将依赖分散到子模块 POM（本仓库为单模块结构）。
- **新增前端依赖**：
  - 仅在 `web/package.json` 中声明，提交后务必同步更新 `web/package-lock.json`，禁止手动编辑 lock 文件。
  - 保持 `^` 语义化版本范围，定期通过 `npm audit` 检查安全漏洞。
- **版本升级**：
  - 升级 Spring Boot 时先升级 parent 版本，再逐一处理 breaking change；AgentScope 多 extension 需保持 `${agentscope.version}` 一致。
  - 升级前在本地执行 `mvn clean test` 与 `cd web && npm run build`，确认无回归。
- **外部服务依赖**：
  - LLM Provider 地址（DashScope 等）通过 `application.yml` 的 `agentscope.*.base-url` 配置，不要在代码中硬编码 URL；测试用例中通过构造请求覆盖 baseUrl。