---
name: Git Push
description: 生成本地提交记录文档，追加到上次 commit 后一并推送到远程仓库
---
请帮我完成以下操作：

1. 运行 `git log -1 --pretty=format:"%H|%s|%an|%ai"` 获取最新一次提交信息
2. 运行 `git diff --name-status HEAD~1 HEAD` 获取本次提交变更的文件清单
3. 通过 bash 命令 `date +%Y%m%d-%H%M%S` 获取当前时间戳
4. 在 `/opt/OfferPilot/doc/提交记录/` 目录下生成提交记录文档
5. 运行 `git add '/opt/OfferPilot/doc/提交记录/'` 将记录文档加入暂存区
6. 运行 `git commit --amend --no-edit` 将记录文档追加到上一次 commit
7. 运行 `git push` 将本地提交（含记录文档）推送到远程仓库

**文档命名规则：** `{yyyyMMdd-HHmmss}-push-{commit简述}.md`

**文档模板：**

```markdown
# {commit message}

## 提交信息

| 项目 | 内容 |
|------|------|
| Commit Hash | {hash} |
| Commit Message | {message} |
| 作者 | {author} |
| 提交时间 | {time} |
| 推送时间 | {push_time} |

## 变更文件清单

| 状态 | 文件路径 |
|------|----------|
| A/M/D | {file_path} |

## 变更摘要

{根据变更文件清单，简要概括本次提交的核心改动内容}
```

**注意：**
- 步骤 5-6 如果记录文档已存在于上一次 commit 中（如 amend 后重跑），则自动跳过无变更
- 文档第一行必须包含版权注释 `# Copyright (c) 2020-06-29 Qoder. All rights reserved.`
- 存储路径必须为 `/opt/OfferPilot/doc/提交记录/`
