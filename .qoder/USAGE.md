# Qoder AI 开发工作流 — 可复用模板

从 Harness 项目提取的 Qoder AI 编排配置，可复用到其他 Java + React 全栈项目。

## 目录结构

```
qoder-reusable/
├── rules/        # 15 个编码规范规则（.mdc 格式）
├── skills/       # 11 个代码生成 Skill（SKILL.md 格式）
├── commands/     # 7 个快捷命令
└── USAGE.md
```

## 使用方法

将 rules/、skills/、commands/ 三个目录复制到目标项目的 .qoder/ 下。

## 需要修改后使用

- Rules 中 java-api-layer.mdc / java-service-test-layer.mdc 的包名 com.harness
- Skills 中输出路径的包名 com.harness 和 Maven groupId harness-*
- Skills 中 mock-db-data / query-db 的 MySQL 客户端路径
- Commands 中 git-push.md 的提交记录输出路径

## 新项目适配步骤

1. 复制 rules/、skills/、commands/ 到 .qoder/
2. 全局替换包名：com.harness → 新项目包名
3. 全局替换 Maven groupId：harness-* → 新项目 artifactId
4. 调整 Skills 中的输出路径
5. 编写新项目的 AGENTS.md 描述技术栈和模块结构
6. 启动 Qoder，AI 会自动生成 repowiki 知识库
