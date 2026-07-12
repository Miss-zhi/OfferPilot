---
kind: build_system
name: Maven + Vite 双端构建与 Docker Compose 编排
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - docker-compose.yml
    - web/package.json
    - web/vite.config.ts
---

## 构建系统概览

本项目采用前后端分离、双构建工具的架构：后端基于 Maven（Spring Boot），前端基于 Vite（React/TypeScript），基础设施通过 Docker Compose 一键拉起。

### 1. 后端构建 — Maven + Spring Boot

- 构建入口：pom.xml，继承 spring-boot-starter-parent:3.2.5，Java 版本锁定为 17。
- 核心插件：
  - spring-boot-maven-plugin：打包可执行 JAR，排除 Lombok。
  - jacoco-maven-plugin:0.8.12：在 test 阶段生成覆盖率报告。
- 依赖管理：所有第三方库版本集中在 properties 中声明（如 agentscope.version=2.0.0-RC5、jjwt.version=0.12.5），AgentScope 各扩展模块统一使用变量引用。
- 测试栈：JUnit 5 + Spring Boot Test + REST Assured + Testcontainers（MySQL），集成测试位于 src/test/java，按 *Test.java / *IT.java 命名区分。
- 运行方式：mvn spring-boot:run 或 mvn package && java -jar target/*.jar；多环境配置通过 application-{dev,prod}.yml profile 切换。

### 2. 前端构建 — Vite + TypeScript

- 构建入口：web/package.json，脚本命令：
  - npm run dev：Vite 开发服务器（端口 3000，代理 /api 到后端 8080）。
  - npm run build：tsc -b 类型检查 + vite build 静态资源打包。
  - npm run lint：oxlint 代码检查。
- 构建产物：输出至 web/dist/，由后端静态资源目录托管或通过 Nginx 部署。
- 依赖管理：package-lock.json 锁定版本，生产依赖与开发依赖严格分离。

### 3. 容器化与编排 — Docker Compose

- 编排文件：docker-compose.yml，仅编排基础设施服务（应用本身通过 IDE/Maven 本地启动）：
  - MySQL 8.0（3306）、Redis 7（6379）、Milvus 2.4.6（19530）、etcd 3.5.14（2379）、MinIO（9001）、WebSearch MCP（3000）。
  - 所有服务均配置 healthcheck 与 restart: unless-stopped，数据持久化通过 named volumes。
  - 敏感配置通过 .env 文件注入（如 MYSQL_ROOT_PASSWORD、MINIO_ACCESS_KEY）。
- Dockerfile：编码规范文档中提及存在 Dockerfile，但当前仓库未包含实际文件，推测用于生产镜像构建。

### 4. 发布与部署约定

- 版本号策略：后端 pom.xml 使用 1.0.0-SNAPSHOT，前端 web/package.json 使用 0.0.0，均为开发快照版本，无语义化版本发布流程。
- CI/CD：仓库未发现 .github/workflows、Jenkinsfile、Makefile 等 CI 配置文件，本地开发为主。
- 数据库迁移：SQL 变更脚本集中存放于 Documents/Sql变更/，按时间戳命名，需手动执行。

### 开发者应遵循的规则

1. 新增依赖：优先在 pom.xml 的 properties 中声明版本，避免硬编码版本号。
2. 前端改动：修改 web/ 下代码后，使用 npm run build 验证构建，确保类型检查通过。
3. 本地联调：先 docker compose up -d 拉起基础设施，再分别启动后端（IDE 或 mvn spring-boot:run）和前端（npm run dev）。
4. 测试：单元测试用 mvn test，集成测试用 mvn verify（自动启动 Testcontainers 容器）。
5. 环境变量：所有密钥类配置放入 .env，不要提交到 Git（已在 .gitignore 中忽略）。