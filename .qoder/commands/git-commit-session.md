---
name: Git Commit Session
description: 提交当前会话中修改的文件，生成规范的 commit message
---
请帮我完成以下操作：
1. 运行 `git status` 查看当前变更文件列表
2. 根据当前会话上下文，识别本次会话中修改或创建的文件
3. 仅对这些会话相关文件执行 `git add`（不要 `git add -A`）
4. 分析这些文件的变更内容，按 Conventional Commits 规范生成 commit message
5. 使用生成的 message 执行 `git commit`

注意：
- 只提交当前会话涉及的文件，不要提交其他无关变更
- message 应简洁概括本次会话的核心工作内容
