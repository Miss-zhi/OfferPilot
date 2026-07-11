---
kind: build_system
name: 构建与制品管理（Maven + Docker Compose + Vite）
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - docker-compose.yml
    - web/package.json
---

本项目采用前后端分离架构，后端基于 Maven/Spring Boot，前端基于 Vite/React，基础设施通过 Docker Compose 编排。仓库中未发现 CI/CD 流水线、Dockerfile 或 Makefile，构建主要依赖 IDE 和命令行工具完成。

### 1. 后端构建系统（Maven + Spring Boot）
- 根目录 `pom.xml` 继承自 `spring-boot-starter-parent:3.2.5`，Java 版本锁定为 17。
- 核心插件：
  - `spring-boot-maven-plugin`：打包可执行 JAR，排除 Lombok。
  - `jacoco-maven-plugin`：在 test 阶段生成覆盖率报告。
- 关键属性集中声明于 `<properties>`：`agentscope.version=2.0.0-RC5`、`jjwt.version=0.12.5`，统一管控 AgentScope 扩展与 JWT 依赖版本。
- 测试依赖使用 Testcontainers（MySQL）+ REST Assured 做集成测试，配合 `src/test/resources/application-test.yml` 提供测试配置。
- 运行方式：`mvn spring-boot:run` 或 `mvn package` 后执行生成的 JAR；开发环境通过 `application-dev.yml` 切换 H2 内存库。

### 2. 前端构建系统（Vite + TypeScript）
- 位于 `web/package.json`，脚本命令：
  - `npm run dev` — 启动 Vite 开发服务器
  - `npm run build` — 先 `tsc -b` 类型检查，再 `vite build` 产出静态资源到 `web/dist`
  - `npm run lint` — 使用 oxlint 进行代码检查
  - `npm run preview` — 预览构建产物
- 无独立 Webpack/Vite 配置文件覆盖默认行为，仅通过 `tsconfig.*.json` 管理多项目 TS 编译。

### 3. 基础设施与环境编排（Docker Compose）
- `docker-compose.yml` 定义开发环境所需的全部外部服务：
  - MySQL 8.0（3306）、Redis 7（6379）、Milvus 2.4.6（19530）、etcd 3.5.14（2379）、MinIO（9001）、WebSearch MCP（3000）
- 所有容器均配置 healthcheck 与 `restart: unless-stopped`，数据通过命名卷持久化。
- 应用本身不通过 Compose 启动，注释明确“应用通过 IDE/Maven 本地启动”。

### 4. 制品与发布流程
- 未检出任何 `Dockerfile`、CI 配置文件（`.github/workflows`、Jenkinsfile、GitLab CI 等）或发布脚本。
- 版本号策略：后端 `pom.xml` 使用 `1.0.0-SNAPSHOT`，前端 `package.json` 使用 `0.0.0`，均为占位值，未见自动化版本递增逻辑。
- 文档 `Documents/05-部署运维手册.md` 与 `Documents/提示词/模板/03-05-工作流程-deploy.md` 描述了部署步骤，但仓库内未包含实际部署脚本。

### 开发者应遵循的规则
- 新增后端依赖时，优先在 `pom.xml` 的 `<properties>` 中声明版本常量，避免散落的硬编码版本。
- 单元测试使用 JUnit 5 + Mockito，集成测试使用 `@SpringBootTest` + Testcontainers，保持 `*Test.java` / `*IT.java` 命名约定。
- 前端新增依赖需同步更新 `web/package.json`，并通过 `npm run lint` 保证代码风格一致。
- 本地联调前应先 `docker compose up -d` 拉起全部基础设施，确保端口未被占用且健康检查通过。