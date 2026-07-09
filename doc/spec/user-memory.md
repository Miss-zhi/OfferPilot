# 用户长期记忆设计

> **来源**：《03-详细设计说明书》§5  
> **模块**：用户长期记忆（DB 存储，按 userId 隔离）  
> **对应表**：[database-schema.md](database-schema.md) §8 `op_user_memory`

---

## 设计动机

**为什么不用 MEMORY.md 文件：** 多用户场景下，文件方式无法按用户隔离记忆。改为数据库表 `op_user_memory`，每条记忆绑定 `user_id`，Agent 读写记忆时自动按当前登录用户过滤。

## 记忆分类

| 类别 | 说明 | 示例 |
|:---|:---|:---|
| PROFILE | 用户画像 | 目标公司、岗位、当前水平 |
| WEAK_POINT | 薄弱点追踪 | HashMap 50→82 分、JVM 内存模型 55→65 分 |
| PREFERENCE | 面试偏好 | 表达容易紧张、建议总分总结构 |
| PLAN | 学习计划进度 | 本周重点 JVM GC、下周系统设计 |
| GENERAL | 其他记忆 | 用户提到的个人背景、特殊需求 |

## UserMemoryService 核心代码

```java
@Service
public class UserMemoryService {

    private final UserMemoryRepository memoryRepo;

    /** Agent 读取用户记忆（注入到 system prompt） */
    public String loadUserMemory(String userId) {
        List<UserMemory> memories = memoryRepo.findByUserIdOrderByCategoryRelevanceScoreDesc(userId);

        StringBuilder sb = new StringBuilder("# 用户记忆\n\n");
        Map<String, List<UserMemory>> grouped = memories.stream()
            .collect(Collectors.groupingBy(UserMemory::getCategory));

        grouped.forEach((category, list) -> {
            sb.append("## ").append(categoryLabel(category)).append("\n");
            list.forEach(m -> sb.append("- ").append(m.getMemoryContent()).append("\n"));
            sb.append("\n");
        });

        // 更新访问计数
        memories.forEach(m -> {
            m.setAccessCount(m.getAccessCount() + 1);
            m.setLastAccessed(Instant.now());
        });
        memoryRepo.saveAll(memories);

        return sb.toString();
    }

    /** Agent 写入/更新记忆 */
    public void saveMemory(String userId, String key, String content, String category) {
        UserMemory memory = memoryRepo.findByUserIdAndMemoryKey(userId, key)
            .orElse(new UserMemory());
        memory.setUserId(userId);
        memory.setMemoryKey(key);
        memory.setMemoryContent(content);
        memory.setCategory(category);
        memoryRepo.save(memory);
    }

    /** Agent 删除过时记忆 */
    public void removeMemory(String userId, String key) {
        memoryRepo.deleteByUserIdAndMemoryKey(userId, key);
    }
}
```

## Agent 集成方式

用户记忆不再通过 `ChatController` 静态拼接 system prompt，而是由 `MemoryInjectMiddleware` 在每次推理前动态注入（详见《04-实现与编码规范》中"Middleware 示例"章节）。这解决了两个关键问题：

1. **时效性**：Agent 被 Caffeine 缓存 30 分钟，静态注入导致记忆变更无法被缓存中的 Agent 感知。Middleware 方案每次推理前重新从 DB 加载，保证记忆始终最新。
2. **正确性**：原方案中 `ChatController` 加载了 `userMemory` 但 `AgentFactory.buildAgent(userId)` 并未使用该变量——记忆功能实际未生效。

Agent 在对话过程中也可以通过工具主动更新记忆（比如发现用户新的薄弱点时自动记录）。记忆写入后下次推理立即生效——因为 `MemoryInjectMiddleware.onSystemPrompt` 每次都会重新从 DB 加载。
