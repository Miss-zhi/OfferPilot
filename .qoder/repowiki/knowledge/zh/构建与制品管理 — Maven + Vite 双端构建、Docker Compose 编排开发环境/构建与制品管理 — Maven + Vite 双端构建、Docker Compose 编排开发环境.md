---
kind: build_system
name: 构建与制品管理 — Maven + Vite 双端构建、Docker Compose 编排开发环境
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - docker-compose.yml
---

## 1. 构建系统概览

本项目采用**前后端分离、双构建工具**的单体仓库结构：
- **后端（Java/Spring Boot）**：基于 Maven，使用 Spring Boot Starter Parent 统一管理依赖版本，通过 `spring-boot-maven-plugin` 打包为可执行 JAR。
- **前端（React/Vite）**：独立位于 `web/` 目录，使用 Vite 作为构建器，TypeScript 编译 + oxlint 静态检查。
- **开发环境编排**：通过 `docker-compose.yml` 一键拉起 MySQL、Redis、Milvus、etcd、MinIO、WebSearch 等基础设施容器，应用本身通过 IDE/Maven 本地启动（注释明确说明）。
- **无 CI/CD 流水线**：仓库中未发现 GitHub Actions、Jenkinsfile、Makefile、Dockerfile 等自动化构建与发布脚本。

## 2. 关键文件与职责

| 文件 | 作用 |
|------|------|
| `pom.xml` | 后端 Maven 工程定义，声明 Spring Boot 3.2.5 父 POM、AgentScope v2、JWT、Milvus SDK、Testcontainers 等依赖；配置 JaCoCo 代码覆盖率插件 |
| `web/package.json` | 前端 npm 工程，定义 dev/build/lint/preview 脚本及 React 19 + Ant Design 6 + Vite 8 技术栈 |
| `docker-compose.yml` | 开发环境编排，包含 MySQL 8、Redis 7、Milvus 2.4.6、etcd 3.5、MinIO、Open Web Search 等服务及其健康检查与数据卷 |
| `src/main/resources/application*.yml` | 多环境配置（dev/prod），配合 Testcontainers 在集成测试中使用 H2/MySQL |
| `workspace/admin/agents/...` | AgentScope Studio 会话日志（运行时产物，非构建产物） |

## 3. 架构与约定

### 3.1 后端构建约定
- **版本管理**：通过 `<properties>` 集中声明 `java.version=17`、`agentscope.version=2.0.0-RC5`、`jjwt.version=0.12.5`，避免散落的硬编码版本号。
- **依赖范围**：数据库驱动（H2/MySQL）、JWT 实现类、Lombok 等均标记为 `runtime` 或 `optional`，保持生产包精简。
- **测试策略**：单元测试使用 `spring-boot-starter-test` + `spring-security-test`；集成测试使用 `testcontainers` 动态拉起 MySQL，REST API 测试使用 `rest-assured`。
- **覆盖率**：JaCoCo 在 `test` 阶段自动生成报告，目标路径 `target/site/jacoco/index.html`。

### 3.2 前端构建约定
- **模块系统**：`type: module` 启用原生 ES Module，配合 `tsconfig.app.json` / `tsconfig.node.json` 分离应用与 Node 类型。
- **脚本命令**：`npm run build` 先执行 `tsc -b` 进行增量类型检查，再调用 `vite build` 生成静态资源至 `web/dist/`。
- **代码质量**：oxlint 替代 ESLint，配置文件 `.oxlintrc.json` 位于 `web/` 根目录。

### 3.3 环境与部署约定
- **开发模式**：`docker-compose up` 仅启动基础设施，后端通过 IDE 直接运行 `OfferPilotApplication`，便于热重载调试。
- **配置隔离**：`application-dev.yml` / `application-prod.yml` 按环境拆分，敏感信息通过 `.env` 注入（如 `MYSQL_ROOT_PASSWORD`、`MINIO_ACCESS_KEY`）。
- **数据持久化**：所有外部服务均挂载命名卷（`mysql-data`、`redis-data`、`milvus-data` 等），重启不丢数据。

## 4. 开发者应遵循的规则

1. **新增依赖**：统一在 `pom.xml` 的 `<properties>` 或 `<dependencyManagement>` 中声明版本，禁止在业务模块中重复指定。
2. **测试编写**：需要外部服务的测试必须使用 `@SpringBootTest` + Testcontainers，参考 `AbstractServiceIT` / `AbstractControllerIT` 基类。
3. **前端变更**：修改 `web/` 下代码后，先执行 `npm run lint` 再 `npm run build`，确保类型与语法无误。
4. **环境配置**：新增环境变量时同步更新 `docker-compose.yml` 的 `environment` 段和对应 `application-*.yml`，并在 `.env` 中添加默认值。
5. **构建产物**：`target/`、`web/dist/`、`workspace/admin/agents/.../sessions/*.jsonl` 均为构建/运行产物，已在 `.gitignore` 中排除，不要提交到版本库。

## 5. 缺失与改进建议

- **缺少 Dockerfile**：当前仅有 compose 编排，未提供应用镜像构建入口，建议补充多阶段 Dockerfile 以支持容器化部署。
- **缺少 CI 流水线**：无 GitHub Actions / Jenkins 配置，建议增加 PR 触发构建、测试、覆盖率门禁流程。
- **缺少 Makefile / 脚本**：项目根目录存在空 `scripts/` 目录，可考虑引入 Makefile 统一 `make build`、`make test`、`make docker` 等常用命令。
- **版本策略**：`pom.xml` 使用 `1.0.0-SNAPSHOT`，未结合 Git tag 自动递增，建议引入 `maven-release-plugin` 或 `semantic-versioning` 规范。
