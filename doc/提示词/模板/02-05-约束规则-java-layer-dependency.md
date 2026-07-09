# java-layer-dependency.mdc

## 概念

防止 AI 跨层调用，确保单向依赖。

## 演示操作

在 Qoder 里输入：

```
帮我在 .qoder/rules/ 下生成 java-layer-dependency.mdc
type always-on，只对 Java 生效
依赖方向：Web→Service→Domain
Web 不能直接调 Domain
禁止循环依赖
```
