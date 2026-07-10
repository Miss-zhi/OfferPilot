---
kind: dependency_management
name: 前后端双栈依赖管理（Maven + npm）
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/package-lock.json
    - docker-compose.yml
---

## 1. 使用的系统与工具
- **后端 Java**：基于 Maven，以 `spring-boot-starter-parent:3.2.5` 作为父 POM，统一继承 Spring Boot BOM 管理的版本。
- **前端 Web**：基于 npm，使用 Vite + React + TypeScript，通过 `package.json` 声明依赖，并锁定在 `web/package-lock.json` 中。
- **基础设施依赖**：通过 `docker-compose.yml` 编排 MySQL 8、Redis 7、Milvus 2.4.6、etcd、MinIO 等运行时服务，应用侧不直接声明这些镜像的版本。

## 2. 关键文件与位置
- 后端依赖清单与构建配置：`pom.xml`
- 前端依赖清单与锁文件：`web/package.json`、`web/package-lock.json`
- 基础设施容器编排：`docker-compose.yml`
- 环境变量入口：`.env`（配合 docker-compose 注入数据库/MinIO 等凭据）

## 3. 架构与约定
### 后端（Maven）
- 采用单一根 `pom.xml` 聚合所有依赖，未拆分子模块；通过 `<properties>` 集中定义跨依赖版本号（如 `agentscope.version`、`jjwt.version`），避免散落的硬编码版本。
- 借助 `spring-boot-starter-parent` 的 dependencyManagement 能力，Spring 生态组件（Web、Security、JPA、Redis、Test 等）均省略 `<version>`，由父 POM 统一管理。
- 运行期依赖按环境拆分 scope：H2 仅 `runtime` 用于开发调试，MySQL Connector/J 同样 `runtime` 指向生产；测试依赖（Testcontainers、REST Assured、spring-security-test）统一标记为 `test`。
- 构建插件集中在 `<build><plugins>` 中：`spring-boot-maven-plugin` 排除 Lombok 打包，`jacoco-maven-plugin` 集成覆盖率报告。
- 第三方 SDK 直接声明版本（如 Milvus SDK 2.4.1、OkHttp 4.12.0、PDFBox 3.0.2、Apache POI 5.2.5），未引入额外的 BOM 或私有仓库。

### 前端（npm）
- 依赖分为 `dependencies` 与 `devDependencies` 两类，核心业务库（antd、axios、react、zustand、echarts 等）放在前者，构建与类型工具（vite、typescript、oxlint、@types/*）放在后者。
- 使用 `package-lock.json`（lockfileVersion 3）锁定精确子依赖树，确保团队与 CI 安装结果一致。
- 脚本命令集中在 `scripts` 字段：`dev`、`build`、`lint`、`preview`，未引入额外包管理器封装。

### 基础设施依赖
- `docker-compose.yml` 显式声明各服务的镜像及端口映射，并通过 `${VAR:-默认值}` 形式从 `.env` 注入敏感信息，使本地一键拉起完整运行环境。
- 应用代码通过 Spring Profile（`application-dev.yml` / `application-prod.yml`）切换数据源、缓存、向量库等外部依赖连接参数。

## 4. 开发者应遵循的规则
- **新增后端依赖**：优先放入 `<properties>` 集中声明版本；若属于 Spring 生态则省略 version 交由 parent 管理；非 Spring 组件需显式指定版本并尽量复用已有 properties。
- **区分 scope**：仅开发/测试阶段需要的依赖必须标注 `test` 或 `runtime`，避免污染最终产物体积。
- **前端依赖升级**：修改 `web/package.json` 后务必提交更新后的 `web/package-lock.json`，禁止手动编辑 lock 文件。
- **基础设施变更**：调整 docker-compose 中的镜像版本时，同步更新注释中的版本说明，并在 `.env` 中维护对应变量默认值。
- **无私有仓库/代理**：当前项目未配置 Maven 私有仓库或 npm registry 镜像，依赖均拉取自公共仓库；如需接入企业内网，应在 pom.xml 的 `<repositories>` 与 npm 的 `.npmrc` 中补充。