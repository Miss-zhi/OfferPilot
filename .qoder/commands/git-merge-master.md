---
name: Git Merge Master
description: 提交所有变更并合并到 master 分支
---
请帮我完成以下操作：
1. 运行 `git status` 查看当前所在分支和变更文件
2. 执行 `git add -A` 暂存所有变更
3. 分析变更内容，按 Conventional Commits 规范生成 commit message
4. 执行 `git commit` 提交到当前分支
5. 切换到 master 分支：`git checkout master`
6. 合并当前分支：`git merge <原分支名>`
7. 切回原分支：`git checkout <原分支名>`

注意：
- 如果当前已在 master 分支，直接提交即可，无需合并
- 合并前确认工作区干净，如有冲突提示用户处理
