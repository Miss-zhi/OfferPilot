---
name: create-service
description: 生成业务层服务类，封装完整的 CRUD 逻辑，直接操作 JPA Entity + Repository。当需要为某个实体新增业务服务时使用。
---

# create-service

## 目的
生成 Service 层服务类，封装业务逻辑。

## 前置规范
**必须先读取并严格遵循** `.qoder/rules/java-service-layer.mdc`

## 工作流
1. 确认实体名称
2. 确认对应的 Repository
3. 生成 Service 服务类
4. 输出到 src/main/java/com/tutorial/offerpilot/service/

## 输出文件

| 文件 | 路径 |
|------|------|
| Service | `src/main/java/com/tutorial/offerpilot/service/{Name}Service.java` |

## 约束
- 方法参数必须是**基础类型**（Long / String / Integer 等）
- 返回值必须是**基础类型**、**Entity 对象**、**DTO 对象**或 **Tuple 类型**
- 禁止在 Service 层定义任何数据类（DTO / VO / Request）
- 禁止方法参数使用 Entity 或 Form 对象
- 创建 Entity 时必须设置 `setCreateBy("SYSTEM")` / `setUpdateBy("SYSTEM")`
- 更新 Entity 时必须设置 `setUpdateBy("SYSTEM")`
- 必须包含版权注释
