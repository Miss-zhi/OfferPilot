---
name: Git Commit All
description: 提交所有变更，包含详细分析说明
---
请帮我完成以下操作：
1. 运行 `git diff --stat` 和 `git diff` 查看所有变更
2. 分析所有变更内容，按 Conventional Commits 规范生成 commit message
3. 为每个变更的文件简述改动原因
4. 执行 `git add -A`
5. 使用生成的 message 执行 `git commit`

要求：commit message 应覆盖所有变更文件的核心改动点。
