
# 简历智能诊断增强 — 实施计划

## 变更概要

新增 3 个 @Tool 工具 + 3 个返回 DTO + ResumeService 重构 + AgentFactory 更新。共涉及 9 个文件，新增 6 个文件，修改 3 个文件。

---

## Task 1: 新建 DTO — JdMatchResult、StarCheckResult、QualityCheckResult

**文件路径：** `src/main/java/com/tutorial/offerpilot/dto/tool/`

在 `dto/tool/` 下新建 3 个返回 POJO，遵循现有 DTO 风格（`@Data @NoArgsConstructor @AllArgsConstructor`）。

- **JdMatchResult.java** — JD 匹配结果
  - `guidance` (String): LLM 评估指导文本
  - `score` (int): 匹配度评分 0-100
  - `matchRate` (double): 匹配率百分比
  - `matched` (List\<String\>): 简历已匹配技能
  - `missing` (List\<String\>): JD 中缺失技能

- **StarCheckResult.java** — STAR 检查结果
  - `guidance` (String): LLM 评估指导文本
  - `items` (List\<StarItem\>): 每段经历的检测结果
  - `totalCount` (int): 经历总数
  - 内部静态类 `StarItem`: `index` (int), `content` (String)

- **QualityCheckResult.java** — 简历质量检查结果
  - `guidance` (String): LLM 评估指导文本
  - `rawData` (List\<RawData\>): 原始文本片段
  - `totalIssues` (int): 问题总数（由 LLM 填充，工具返回 0）
  - 内部静态类 `RawData`: `section` (String), `content` (String), `stats` (String)

**验证：** 编译通过，字段与需求文档 §4.2-4.4 一致。

---

## Task 2: 重构 ResumeService — 新增 3 个方法 + 修正 extractSkills

**文件路径：** `src/main/java/com/tutorial/offerpilot/service/ResumeService.java`

### 2.1 修正 `extractSkills()`（第 199-221 行）

将当前的硬编码正则匹配（`"熟练掌握|精通|熟悉…"`）替换为纯文本段落定位：

```
提取技能相关行（纯文本处理，不含语义判断）。
定位"技能"章节 → 返回原文片段（含 "：" / ":" / "、"的行）。
不负责：识别具体技能名称、判断技能类别、匹配岗位要求。
```

### 2.2 新增 `evaluateResume(String resumeText, String jdText)` 重载

在现有单参数 `evaluateResume(String)` 基础上新增带 JD 的重载。当 `jdText` 非空时，调用 `matchJd()` 并在 guidance 中追加 JD 匹配分析段落。

### 2.3 新增 `matchJd(String resumeText, String jdText)` 

实现需求文档 §4.2 的 JD 匹配度计算逻辑：
- 调用 `extractKeywords()` (纯文本切分，不分词) 提取 JD 和简历的关键词集合
- 计算交集 `matched`、差集 `missing`、覆盖率 `matchRate`
- 构建 LLM 评估指导文本（含 matched/missing 清单）
- 返回 `JdMatchResult`

### 2.4 新增 `analyzeStar(String resumeText)` 

实现需求文档 §4.3 的 STAR 检查逻辑：
- 调用 `splitExperiences()` 按"项目/工作/实习经历"切分段落
- 调用 `buildStarGuidance()` 构建 LLM 指导
- 返回 `StarCheckResult`

辅助方法：
- `splitExperiences(String text)`: 按正则 `(?=(?:项目|工作|实习)(?:经历|经验|描述))` 切分，过滤技能/教育章节
- `buildStarGuidance(List<StarItem>)`: 生成包含 S/T/A/R 四要素定义的指导文本

### 2.5 新增 `checkQuality(String resumeText)` 

实现需求文档 §4.4 的简历质量专项检查：
- 调用 `extractSkillLines()` 提取技能段落原文
- 调用 `extractExperienceLines()` 提取项目经历段落原文
- 统计 `quantifiedCount`（含 %/倍/万/千 等量词的段落数）
- 调用 `buildQualityGuidance()` 构建 LLM 指导
- 返回 `QualityCheckResult`

辅助方法：
- `extractSkillLines(String)`: 纯文本定位技能章节
- `extractExperienceLines(String)`: 纯文本定位经历章节
- `buildQualityGuidance(RawData, quantifiedCount, totalExpCount)`: 生成三项检查指导

### 2.6 新增 `extractKeywords(String text)` — 纯文本关键词辅助方法

从文本中提取关键词集合（按标点切分、去重、过滤短词），不做语义分词（语义判断留给 LLM）。

**验证：** 编译通过，无硬编码技术栈关键词，所有方法仅做文本锚点定位和数据统计。

---

## Task 3: 新建 @Tool 工具 — StarCheckTool、ResumeQualityTool

**文件路径：** `src/main/java/com/tutorial/offerpilot/agent/tool/`

遵循现有 Tool 模式（`@Component + @RequiredArgsConstructor + @Tool + @ToolParam`）。

### 3.1 **StarCheckTool.java**

```java
@Tool(name = "check_star", description = "检查简历中每段项目/工作经历的STAR四要素（情境-任务-行动-结果）完整性")
public StarCheckResult checkStar(
    @ToolParam(name = "resume_text") String resumeText)
```

### 3.2 **ResumeQualityTool.java**

```java
@Tool(name = "check_resume_quality", description = "专项检查简历的3类常见问题：技能层次分类、量化数据覆盖度、技术栈罗列vs业务成果")
public QualityCheckResult checkResumeQuality(
    @ToolParam(name = "resume_text") String resumeText)
```

**验证：** 编译通过，日志记录完整。

---

## Task 4: 更新 ResumeEvaluateTool — 新增 jd_text 可选参数

**文件路径：** `src/main/java/com/tutorial/offerpilot/agent/tool/ResumeEvaluateTool.java`

- `evaluate_resume` 方法新增可选参数 `jd_text`：
  ```java
  @ToolParam(name = "jd_text", description = "目标岗位JD文本（可选，提供时可计算简历匹配度）", required = false)
  ```
- 当 `jdText` 非空时，调用 `resumeService.evaluateResume(resumeText, jdText)`
- 更新 `@Tool` description 反映新增能力
- 更新 `ResumeEvaluateResult` 新增 `jdMatchGuidance` 字段存放 JD 匹配指导

**验证：** 编译通过，与原 evaluate_resume 接口向后兼容。

---

## Task 5: 更新 AgentFactory — 工具注册 + resume_coach 更新

**文件路径：** `src/main/java/com/tutorial/offerpilot/agent/AgentFactory.java`

### 5.1 注入新 Tool 实例（构造器参数新增）

- 新增 `StarCheckTool starCheckTool`
- 新增 `ResumeQualityTool resumeQualityTool`

### 5.2 buildToolkit() 注册新工具到 resume_analysis 分组

```java
toolkit.registration().tool(starCheckTool).group("resume_analysis").apply();
toolkit.registration().tool(resumeQualityTool).group("resume_analysis").apply();
```

### 5.3 buildPermissions() 新增 permission rules

```java
.addAllowRule("check_star", ...)
.addAllowRule("check_resume_quality", ...)
```

### 5.4 buildSystemPrompt() 更新工具说明

补充 `check_star`、`check_resume_quality` 的 guidance 解释。

### 5.5 buildResumeAgent() 更新 SysPrompt + 工具白名单

- `inlineAgentsBody` 更新为需求文档 §4.6 的流程（调用 check_star → check_resume_quality → match_jd → 对标优秀简历）
- `tools` 白名单更新为：
  ```java
  List.of("parse_resume", "evaluate_resume", "check_star", "check_resume_quality", "match_jd", "search_questions")
  ```
- 注：`match_jd` 不是独立 @Tool，而是 `evaluate_resume` 带 `jd_text` 参数时内部调用，但 Agent 不知道这一点，只需在 SysPrompt 中指示 "如果用户提供了 JD，调用 evaluate_resume 时传入 jd_text 参数"。

**验证：** 编译通过，toolkit 工具数从 12+1 增至 14+1，resume_analysis 分组从 2 增至 4 个工具。

---

## Task 6: 验证与编译

1. `mvn compile -pl .` 编译通过
2. 运行现有 `ResumeServiceTest` + `ResumeServiceIT` 确认不破坏现有测试
3. 确认 `extractSkills()` 不再包含硬编码技术栈正则
4. 确认所有新增 DTO 的 LLM 字段初始化为 null/空（遵循"工具不替代 LLM"原则）

---

## 不涉及的部分

- **无数据库变更** — 所有结果均为工具返回值，不持久化
- **无 REST API 变更** — 通过 ChatController 对话接口触发
- **前端** — 可选新增 JD 输入区，非本次 P0 范围
