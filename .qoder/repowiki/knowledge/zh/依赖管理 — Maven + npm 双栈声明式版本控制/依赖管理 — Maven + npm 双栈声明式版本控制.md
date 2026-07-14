---
kind: dependency_management
name: 依赖管理 — Maven + npm 双栈声明式版本控制
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/package-lock.json
---

## 1. 使用的系统与工具
- **后端（Java）**：Maven，基于 `spring-boot-starter-parent:3.2.5` 统一继承 Spring Boot BOM，通过 `<properties>` 集中声明公共版本号。
- **前端（React/Vite）**：npm，使用 `package.json` 声明依赖，配合 `package-lock.json`（lockfileVersion=3）锁定精确版本树。
- **构建产物**：无 vendoring；后端依赖由 Maven 仓库解析，前端依赖由 npm registry 解析并落盘到 `web/node_modules`。

## 2. 关键文件与位置
- `pom.xml` — 后端唯一依赖清单，定义 Java 17、AgentScope v2、JWT、Milvus SDK、PDF/DOCX 解析、日志编码器等全部依赖及 scope。
- `web/package.json` — 前端依赖清单，区分 `dependencies` 与 `devDependencies`。
- `web/package-lock.json` — 前端完整依赖快照，包含每个包的 `resolved` URL 与 `integrity` hash。
- `docker-compose.yml` — 运行时外部服务（MySQL、Redis、Milvus）以容器形式提供，不纳入代码依赖管理。

## 3. 架构与约定
- **Spring Boot BOM 继承**：通过 parent POM 统一管理 Spring 生态组件版本，避免手动指定 `spring-*` 各子模块版本。
- **集中属性化版本**：在 `<properties>` 中声明 `agentscope.version`、`jjwt.version`，所有相关依赖引用该变量，保证 AgentScope 多模块（core/harness/openai/dashscope/anthropic/gemini/ollama/studio）版本一致。
- **作用域划分清晰**：数据库驱动（H2、mysql-connector-j）、Lombok、配置处理器等按 `runtime` / `optional` 标记，仅打包所需内容；测试依赖（testcontainers、rest-assured、spring-security-test）严格限定 `scope=test`。
- **插件即策略**：`spring-boot-maven-plugin` 排除 Lombok 参与打包；`jacoco-maven-plugin` 绑定 test 阶段生成覆盖率报告。
- **前端 lockfile 上库**：`package-lock.json` 随仓库提交，确保团队与 CI 安装结果可复现。

## 4. 开发者应遵循的规则
- 新增后端依赖时优先检查是否已被 Spring Boot parent BOM 覆盖；若需固定版本，放入 `<properties>` 并通过变量引用，避免散落的硬编码版本号。
- 对跨模块共享的第三方库（如 AgentScope 系列扩展、JWT 三件套）一律通过 `<properties>` 集中声明，禁止在多个 `<dependency>` 中重复写死版本。
- 仅在编译期或运行期真正需要的依赖才引入，并使用正确的 `scope`（`compile`/`runtime`/`test`/`provided`/`optional`），减少最终包体积。
- 前端新增依赖后必须提交更新后的 `package-lock.json`，禁止只改 `package.json` 而不锁版本。
- 不要将 `node_modules` 或 Maven `.m2/repository` 加入版本控制；如需离线构建，应在 CI 层配置私有镜像或缓存策略，而非 vendoring。