/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.NextQuestionResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.InterviewSession;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模拟面试出题工具 — 提供结构化出题指导，由 LLM 据此动态生成个性化面试题。
 *
 * <p>职责划分：
 * <ul>
 *   <li>本工具：查 DB（session/历史题目/得分）→ 计算进度/难度/阶段 → 输出出题指导</li>
 *   <li>LLM：根据指导信息，结合上下文、公司、岗位自由生成面试题</li>
 * </ul>
 *
 * <p>不再硬编码任何题目模板，所有自然语言内容由 LLM 生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockInterviewTool {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "session[=:]([a-zA-Z0-9_-]+)");

    /** 面试阶段轮转序列 — 仅用于默认阶段的数学轮转，不涉及任何自然语言内容 */
    private static final List<String> PHASE_SEQUENCE = List.of(
            "自我介绍", "专业技能", "专业技能", "项目经验",
            "专业技能", "情景分析", "行为面试", "项目经验",
            "专业技能", "情景分析", "行为面试", "职业规划"
    );

    private final InterviewQuestionRepository questionRepo;
    private final InterviewSessionRepository sessionRepo;

    @Tool(name = "generate_next_question",
            description = "获取下一个面试题的出题指导（含岗位、阶段、难度、历史题目），由你据此生成并提问")
    public NextQuestionResult generateNextQuestion(
            @ToolParam(name = "context", description = "当前面试上下文，包括已有问答历史和面试方向") String context) {
        log.info("generate_next_question called: contextLen={}", context != null ? context.length() : 0);

        String sessionId = parseSessionId(context);
        int questionNumber = countPreviousQuestions(context) + 1;
        String role = extractRole(context, sessionId);
        String category = determineCategory(context, questionNumber);
        String difficulty = determineDifficulty(context, sessionId);
        Double avgScore = fetchAverageScore(sessionId);
        List<String> prevQuestions = fetchPreviousQuestions(sessionId);
        String guidance = buildGuidance(role, category, difficulty, questionNumber, avgScore);

        persistQuestion(sessionId, guidance, category, difficulty);

        log.info("generate_next_question: sessionId={}, role={}, category={}, difficulty={}, qNo={}, avgScore={}",
                sessionId, role, category, difficulty, questionNumber, avgScore);
        return new NextQuestionResult(guidance, category, difficulty, role, questionNumber, avgScore, prevQuestions);
    }

    // ======================== 出题指导构建 ========================

    /**
     * 构建出题指导文本，作为 LLM 生成面试题的指令。
     * 不包含任何硬编码题目文本，仅提供上下文信息。
     */
    private String buildGuidance(String role, String category, String difficulty, int questionNumber, Double avgScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为").append(role).append("岗位生成一道").append(category)
                .append("类面试题，难度").append(difficulty)
                .append("，这是第").append(questionNumber).append("题。");

        if (avgScore != null) {
            sb.append("历史均分").append(String.format("%.0f", avgScore)).append("分。");
        }

        sb.append("请根据岗位特点和面试阶段自由发挥，直接输出题目文本，不要加任何前缀说明。");
        return sb.toString();
    }

    // ======================== 数据查询 ========================

    /** 从 session 获取历史均分。 */
    private Double fetchAverageScore(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        try {
            List<InterviewQuestion> existing = questionRepo.findBySessionIdOrderBySortOrder(sessionId);
            if (existing.isEmpty()) {
                return null;
            }
            return existing.stream()
                    .filter(q -> q.getTechScore() != null && q.getExprScore() != null && q.getCoverageScore() != null)
                    .mapToDouble(q -> (q.getTechScore() + q.getExprScore() + q.getCoverageScore()) / 3.0)
                    .average()
                    .orElse(Double.NaN);
        } catch (Exception e) {
            log.debug("Failed to fetch average score: {}", e.getMessage());
            return null;
        }
    }

    /** 从 DB 获取前几题的文本，供 LLM 避免出重复题。 */
    private List<String> fetchPreviousQuestions(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        try {
            return questionRepo.findBySessionIdOrderBySortOrder(sessionId).stream()
                    .map(InterviewQuestion::getQuestionText)
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
        } catch (Exception e) {
            log.debug("Failed to fetch previous questions: {}", e.getMessage());
            return List.of();
        }
    }

    // ======================== 角色提取 ========================

    /**
     * 从 session 或 context 中提取面试岗位/方向。
     * 优先级：DB sessionType > context 模式匹配 > 默认 "该岗位"。
     */
    private String extractRole(String context, String sessionId) {
        if (sessionId != null) {
            try {
                InterviewSession session = sessionRepo.findBySessionId(sessionId).orElse(null);
                if (session != null && session.getSessionType() != null && !session.getSessionType().isBlank()) {
                    return session.getSessionType();
                }
            } catch (Exception e) {
                log.debug("Failed to read sessionType: {}", e.getMessage());
            }
        }
        if (context != null) {
            String role = parseRoleFromContext(context);
            if (role != null) {
                return role;
            }
        }
        return "该岗位";
    }

    /** 从上下文文本中解析面试岗位。 */
    private String parseRoleFromContext(String context) {
        Pattern rolePattern = Pattern.compile(
                "([\\u4e00-\\u9fa5a-zA-Z]{2,12}(?:工程师|经理|设计师|专员|分析师|运营|开发|架构师"
                + "|顾问|销售|市场|客服|HR|行政|财务|法务|教师|医生|护士|编辑|记者))");
        Matcher matcher = rolePattern.matcher(context);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Pattern labelPattern = Pattern.compile(
                "(?:面试方向|面试岗位|目标岗位|应聘岗位|岗位|方向)[：:]\\s*([\\u4e00-\\u9fa5a-zA-Z]{2,20})");
        Matcher labelMatcher = labelPattern.matcher(context);
        if (labelMatcher.find()) {
            return labelMatcher.group(1).trim();
        }
        return null;
    }

    // ======================== 阶段判定 ========================

    /**
     * 根据上下文关键词或面试进度确定当前阶段（类别）。
     * 回退到 PHASE_SEQUENCE 的数学轮转，不包含任何自然语言内容。
     */
    private String determineCategory(String context, int questionNumber) {
        if (context != null) {
            String ctx = context.toLowerCase();
            if (ctx.contains("自我介绍") || ctx.contains("介绍自己") || ctx.contains("开场")) return "自我介绍";
            if (ctx.contains("技能") || ctx.contains("专业") || ctx.contains("技术") || ctx.contains("能力")) return "专业技能";
            if (ctx.contains("项目") || ctx.contains("经验") || ctx.contains("案例")) return "项目经验";
            if (ctx.contains("情景") || ctx.contains("场景") || ctx.contains("如果") || ctx.contains("假设")) return "情景分析";
            if (ctx.contains("行为") || ctx.contains("素质") || ctx.contains("压力") || ctx.contains("冲突")) return "行为面试";
            if (ctx.contains("规划") || ctx.contains("目标") || ctx.contains("未来") || ctx.contains("职业")) return "职业规划";
        }
        return PHASE_SEQUENCE.get((questionNumber - 1) % PHASE_SEQUENCE.size());
    }

    // ======================== 难度判定 ========================

    /**
     * 基于已有得分确定下一题难度（渐进式）。
     */
    private String determineDifficulty(String context, String sessionId) {
        if (context != null) {
            if (context.contains("高级") || context.contains("hard") || context.contains("深入")) return "hard";
            if (context.contains("初级") || context.contains("easy") || context.contains("入门")) return "easy";
        }
        if (sessionId != null) {
            Double avg = fetchAverageScore(sessionId);
            if (avg != null && !avg.isNaN()) {
                if (avg >= 75) return "hard";
                if (avg >= 50) return "medium";
                return "easy";
            }
        }
        int questionNumber = countPreviousQuestions(context) + 1;
        if (questionNumber > 5) return "hard";
        if (questionNumber > 2) return "medium";
        return "easy";
    }

    // ======================== 持久化 ========================

    /**
     * 将出题指导持久化到 InterviewQuestion，并更新 InterviewSession.questionCount。
     * 注意：持久化的是指导文本，实际题目由 LLM 在对话中生成。
     */
    private void persistQuestion(String sessionId, String guidance, String category, String difficulty) {
        if (sessionId == null) {
            return;
        }
        try {
            List<InterviewQuestion> existing = questionRepo.findBySessionIdOrderBySortOrder(sessionId);
            InterviewQuestion question = new InterviewQuestion();
            question.setSessionId(sessionId);
            question.setQuestionText("[GUIDANCE] " + guidance);
            question.setSortOrder(existing.size());
            questionRepo.save(question);

            InterviewSession session = sessionRepo.findBySessionId(sessionId).orElse(null);
            if (session != null) {
                session.setQuestionCount(
                        session.getQuestionCount() != null ? session.getQuestionCount() + 1 : 1);
                sessionRepo.save(session);
            }

            log.info("Guidance persisted: sessionId={}, sortOrder={}", sessionId, existing.size());
        } catch (Exception e) {
            log.warn("Failed to persist guidance: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    // ======================== 工具方法 ========================

    private String parseSessionId(String context) {
        if (context == null) {
            return null;
        }
        Matcher matcher = SESSION_ID_PATTERN.matcher(context);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int countPreviousQuestions(String context) {
        if (context == null) {
            return 0;
        }
        int count = 0;
        int idx = -1;
        while ((idx = context.indexOf('Q', idx + 1)) != -1) {
            count++;
        }
        return count;
    }
}
