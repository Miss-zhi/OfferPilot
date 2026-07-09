---
name: create-domain
description: 生成 Domain 层代码：JPA Entity + Repository，用于新增数据实体。当需要为某个数据库表创建对应的 Entity 和 Repository 时使用。
---

# create-domain

## 目的
生成 Domain 层代码：JPA Entity（继承 BaseEntity）+ Spring Data JPA Repository 接口。

## 前置规范
**必须先读取并严格遵循** `.qoder/rules/java-domain-layer.mdc`

## 工作流
1. 确认实体名称和对应的数据库表
2. 确认字段列表（排除 id / createdAt / updatedAt / createBy / updateBy，这五个由 BaseEntity 提供）
3. 生成 Entity（继承 BaseEntity，使用 JPA 注解）
4. 生成 Repository（继承 JpaRepository）
5. 输出到对应包

## 输出文件

| 文件 | 路径 |
|------|------|
| Entity | `src/main/java/com/tutorial/offerpilot/entity/{Name}.java` |
| Repository | `src/main/java/com/tutorial/offerpilot/repository/{Name}Repository.java` |

## 约束
- Entity 必须继承 `BaseEntity`，禁止重复定义 id / createdAt / updatedAt / createBy / updateBy
- Entity 使用 JPA 注解：`@Entity`、`@Table`、`@Column`、`@Id`、`@GeneratedValue`
- Repository 继承 `JpaRepository<Entity, Long>`
- Repository 命名：`{Name}Repository`
- 必须包含版权注释
