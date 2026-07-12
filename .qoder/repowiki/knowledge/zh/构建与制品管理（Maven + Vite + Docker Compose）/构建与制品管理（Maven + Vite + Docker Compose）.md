---
kind: build_system
name: 构建与制品管理（Maven + Vite + Docker Compose）
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - docker-compose.yml
    - web/package.json
    - web/vite.config.ts
    - src/main/resources/application.yml
    - src/main/resources/application-dev.yml
---

## 1. 构建系统概览
本项目采用**前后端分离、双构建管线**的架构：后端基于 Maven + Spring Boot，前端基于 Vite + React/TypeScript，基础设施通过 Docker Compose 编排。仓库中未发现 CI/CD 流水线文件（如 GitHub Actions、Jenkinsfile），本地开发依赖 IDE 直接运行。

## 2. 后端构建（Maven / Spring Boot）
- **构建工具**：Maven，继承 `spring-boot-starter-parent:3.2.5`，Java 版本锁定为 17。
- **核心插件**：
  - `spring-boot-maven-plugin`：打包可执行 JAR，并排除 Lombok 编译期注解处理器。
  - `jacoco-maven-plugin:0.8.12`：在 `test` 阶段生成覆盖率报告。
- **依赖管理策略**：所有第三方库版本集中在 `<properties>` 或 `<dependencyManagement>` 中声明（如 `agentscope.version=2.0.0-RC5`、`jjwt.version=0.12.5`），AgentScope 各扩展模块统一使用 `${agentscope.version}`。
- **Profile 与环境配置**：默认激活 `dev` profile，使用 H2 内存数据库；生产环境切换 MySQL，通过 `application-prod.yml` 覆盖。
- **测试体系**：JUnit 5 + Spring Boot Test + REST Assured + Testcontainers（MySQL），集成测试类以 `*IT.java` 命名约定区分于单元测试。

## 3. 前端构建（Vite）
- **构建工具**：Vite 8 + TypeScript 6，React 19 + Ant Design 6。
- **脚本命令**：`npm run dev`（本地热重载）、`npm run build`（tsc 类型检查 + vite build 产物）、`npm run lint`（oxlint）、`npm run preview`。
- **开发代理**：`vite.config.ts` 将 `/api` 请求反向代理至 `http://localhost:8080`，实现前后端联调。
- **产物输出**：`web/dist` 目录，静态资源由后端 `FileUploadController` 等接口消费或通过独立静态服务部署。

## 4. 容器化与基础设施编排
- **Docker Compose**：`docker-compose.yml` 仅编排**基础设施服务**（MySQL 8、Redis 7、Milvus 2.4.6、etcd、MinIO、WebSearch MCP），应用本身不在此文件中被镜像化，而是通过 IDE/Maven 本地启动。
- **数据持久化**：所有外部服务均挂载命名卷（`mysql-data`、`redis-data`、`milvus-data` 等），保证开发环境数据可复用。
- **健康检查**：MySQL、Redis、MinIO、Milvus 均配置了 `healthcheck`，Compose 通过 `depends_on` 控制启动顺序。
- **环境变量注入**：敏感配置通过 `.env` 文件注入（如 `MYSQL_ROOT_PASSWORD`、`MINIO_ACCESS_KEY`、`DASHSCOPE_API_KEY`、`JWT_SECRET` 等）。

## 5. 发布与制品
- **后端制品**：`mvn package` 生成 `target/offerpilot-1.0.0-SNAPSHOT.jar`，版本号遵循 `主.次.修订-SNAPSHOT` 语义。
- **前端制品**：`npm run build` 产出 `web/dist` 下的静态文件，当前未定义独立的 NPM publish 流程。
- **无独立 Dockerfile**：仓库根目录未发现 `Dockerfile` 或 `.dockerignore`，生产镜像构建不在本仓库内完成（可能在外部 CI 中补充）。
- **无 Makefile / shell 构建脚本**：项目未提供统一的 `make` 入口，开发者需分别调用 `mvn` 和 `npm` 命令。

## 6. 开发者应遵循的规则
- **依赖版本集中管理**：新增三方库时优先在 `<properties>` 中声明版本变量，避免散落的硬编码版本号。
- **Profile 隔离**：开发/测试/生产配置严格拆分到 `application-{profile}.yml`，敏感信息一律通过环境变量注入，禁止写死在代码中。
- **测试分层**：单元测试以 `*Test.java` 结尾，集成测试以 `*IT.java` 结尾并使用 Testcontainers 拉起真实 MySQL。
- **前后端联调**：前端开发服务器通过 Vite proxy 转发 `/api` 到后端 8080 端口，确保跨域问题在开发环境自动解决。
- **基础设施一键启动**：新成员只需 `docker compose up -d` 即可拉起全部依赖服务，无需手动安装 MySQL/Redis/Milvus。
- **日志与监控**：生产环境启用 Logstash JSON 格式输出，建议配合 ELK/Loki 收集结构化日志。