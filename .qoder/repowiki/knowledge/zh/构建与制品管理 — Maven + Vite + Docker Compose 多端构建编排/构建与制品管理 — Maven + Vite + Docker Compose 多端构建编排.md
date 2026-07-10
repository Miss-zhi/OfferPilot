---
kind: build_system
name: 构建与制品管理 — Maven + Vite + Docker Compose 多端构建编排
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - docker-compose.yml
    - web/package.json
    - web/vite.config.ts
    - Documents/05-部署运维手册.md
---

## 1. 构建系统概览

OfferPilot 采用「后端单体 + 前端独立 SPA」的双端构建模式：
- **后端**：基于 Spring Boot 3.2.5 的 Java 17 单体应用，使用 Maven 作为依赖管理与打包工具，通过 `spring-boot-maven-plugin` 生成可执行 JAR。
- **前端**：位于 `web/` 子目录，基于 Vite 8 + React 19 + TypeScript，通过 `package.json` 脚本驱动开发、构建与预览。
- **基础设施编排**：根级 `docker-compose.yml` 统一启动 MySQL 8.0、Redis 7、Milvus 2.4.6（含 etcd + MinIO）等外部依赖，为前后端提供共享运行环境。
- **容器化部署**：文档中定义了包含应用服务的完整 compose 模板，但仓库未附带实际 `Dockerfile`，当前以本地 IDE/Maven 启动为主。

## 2. 关键文件与职责

| 文件 | 作用 |
|------|------|
| `pom.xml` | 后端唯一构建入口：声明 Spring Boot parent、全部依赖、版本属性、Jacoco 覆盖率插件、spring-boot-maven-plugin 配置 |
| `docker-compose.yml` | 开发环境基础设施编排（MySQL/Redis/Milvus/etcd/MinIO），不含应用服务本身 |
| `web/package.json` | 前端构建脚本：`dev`/`build`/`lint`/`preview`，依赖锁定于 `package-lock.json` |
| `web/vite.config.ts` | Vite 开发服务器代理 `/api → http://localhost:8080`，路径别名 `@ → ./src` |
| `Documents/05-部署运维手册.md` | 生产部署参考模板（含带 `offerpilot-app` 服务的完整 compose 示例） |
| `.env` | 环境变量入口（供 docker-compose 与运行时注入） |

## 3. 架构与约定

### 3.1 后端构建链
- 继承 `spring-boot-starter-parent:3.2.5`，Java 版本固定为 17。
- 所有第三方库版本集中在 `<properties>` 或直接在 `<dependency>` 中声明，无聚合子模块。
- 测试阶段集成 Jacoco（`prepare-agent` + `report`），通过 `mvn test` 自动生成覆盖率报告。
- 集成测试使用 Testcontainers（MySQL）+ REST Assured，无需本地数据库即可运行。
- 日志输出使用 Logstash encoder + Janino 条件表达式，便于结构化采集。

### 3.2 前端构建链
- `vite build` 前执行 `tsc -b` 进行类型检查，确保 TS 编译产物与构建同步。
- 开发时通过 Vite proxy 将 `/api` 请求转发到后端 8080 端口，避免跨域问题。
- Lint 使用 Oxlint（`.oxlintrc.json`），非 ESLint。

### 3.3 环境编排策略
- 开发环境：`docker-compose up` 仅拉起数据层；应用通过 IDE 直接运行 `OfferPilotApplication`。
- 生产部署：参考 `Documents/05-部署运维手册.md` 中的 compose 模板，需额外准备 `Dockerfile` 将 JAR 打包进镜像。
- 配置文件按环境拆分：`application-dev.yml` / `application-prod.yml`，由 Spring Profile 激活。

## 4. 开发者应遵循的规则

1. **新增后端依赖**：在 `pom.xml` 的 `<dependencies>` 中声明，优先复用已有 starter，避免引入重复功能。
2. **新增前端依赖**：在 `web/package.json` 中声明，并通过 `npm install` 更新 `package-lock.json`，禁止手动编辑 lock 文件。
3. **修改基础设施**：同步更新 `docker-compose.yml` 及 `Documents/05-部署运维手册.md` 中的 compose 示例，保持两者一致。
4. **测试覆盖**：新增 Service/Controller 时需配套单元测试与集成测试，Jacoco 覆盖率随 `mvn test` 自动收集。
5. **环境变量**：敏感配置（JWT_SECRET、DASHSCOPE_API_KEY 等）一律通过 `.env` 注入，禁止硬编码到源码或配置文件中。
6. **构建命令**：后端统一使用 `mvn clean package`，前端统一使用 `npm run build`，不自行编写 Makefile 或 shell 脚本。