/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.NextQuestionResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.entity.InterviewSession;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.repository.InterviewSessionRepository;
import com.tutorial.offerpilot.service.InterviewModeService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模拟面试出题工具 — 提供结构化出题指导，由 LLM 据此动态生成个性化面试题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockInterviewTool {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "session[=:]([a-zA-Z0-9_-]+)");

    private final InterviewQuestionRepository questionRepo;
    private final InterviewSessionRepository sessionRepo;
    private final InterviewModeService modeService;

    private String resolveMode(String mode, String sessionId) {
        if (mode != null && !mode.isBlank()) {
            return mode;
        }
        if (sessionId != null) {
            try {
                InterviewSession session = sessionRepo.findBySessionId(sessionId).orElse(null);
                if (session != null && session.getInterviewMode() != null
                        && !session.getInterviewMode().isBlank()) {
                    return session.getInterviewMode();
                }
            } catch (Exception e) {
                log.debug("Failed to read interviewMode: {}", e.getMessage());
            }
        }
        return "TECH_DEEP";
    }

    @Tool(name = "generate_next_question",
            description = "获取下一个面试题的出题指导（含岗位、阶段、难度、历史题目、面试模式），由你据此生成并提问")
    public NextQuestionResult generateNextQuestion(
            @ToolParam(name = "context", description = "当前面试上下文，包括已有问答历史和面试方向") String context,
            @ToolParam(name = "mode", description = "面试模式：TECH_DEEP/BEHAVIOR/SYSTEM_DESIGN/PRESSURE，不传则从 session 读取或默认为 TECH_DEEP", required = false) String mode,
            @ToolParam(name = "resume_text", description = "简历文本（技术深挖模式时用于提取项目关键词）", required = false) String resumeText) {
        log.info("generate_next_question called: contextLen={}, mode={}, hasResume={}",
                context != null ? context.length() : 0, mode, resumeText != null);

        String sessionId = parseSessionId(context);
        String effectiveMode = resolveMode(mode, sessionId);
        int questionNumber = countPreviousQuestions(context) + 1;
        String role = extractRole(context, sessionId);
        String category = determineCategoryByMode(effectiveMode, context, questionNumber);
        String difficulty = determineDifficultyByMode(effectiveMode, context, sessionId, questionNumber);
        Double avgScore = fetchAverageScore(sessionId);
        List<String> prevQuestions = fetchPreviousQuestions(sessionId);

        List<String> projectKeywords = List.of();
        if ("TECH_DEEP".equals(effectiveMode) && resumeText != null && !resumeText.isBlank()) {
            projectKeywords = modeService.extractProjectKeywords(resumeText);
        }

        String guidance = buildGuidanceByMode(effectiveMode, role, category, difficulty,
                questionNumber, avgScore, projectKeywords);

        persistQuestion(sessionId, guidance, category, difficulty);

        log.info("generate_next_question: sessionId={}, role={}, mode={}, category={}, difficulty={}, qNo={}, avgScore={}",
                sessionId, role, effectiveMode, category, difficulty, questionNumber, avgScore);
        return new NextQuestionResult(guidance, category, difficulty, role,
                questionNumber, avgScore, prevQuestions, effectiveMode);
    }

    private String buildGuidanceByMode(String mode, String role, String category,
            String difficulty, int questionNumber, Double avgScore,
            List<String> projectKeywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为").append(role).append("岗位生成一道").append(category)
                .append("类面试题，难度").append(difficulty)
                .append("，这是第").append(questionNumber).append("题。");

        switch (mode) {
            case "TECH_DEEP" -> {
                if (!projectKeywords.isEmpty()) {
                    sb.append("候选人简历涉及以下项目经历，请从中识别关键技术栈并深挖：\n")
                            .append(String.join("\n", projectKeywords))
                            .append("\n请围绕其中一个技术点深挖，要求候选人从原理、实践、优化三个层次回答。");
                } else {
                    sb.append("请深入追问技术原理和底层实现，要求候选人解释'为什么'而非'是什么'。");
                }
            }
            case "BEHAVIOR" -> sb.append("请用STAR框架提问，要求候选人描述具体情境-任务-行动-结果。");
            case "SYSTEM_DESIGN" -> sb.append("请给出一个开放型系统设计问题，要求候选人从需求分析→架构选型→详细设计逐步展开。");
            case "PRESSURE" -> sb.append("这是压力面试模式。请提出有挑战性的问题，并在候选人回答后质疑其方案的边界条件、替代方案和潜在缺陷。");
        }

        if (avgScore != null && !avgScore.isNaN()) {
            sb.append("历史均分").append(String.format("%.0f", avgScore)).append("分。");
        }

        sb.append("直接输出题目文本，不要加前缀说明。");
        return sb.toString();
    }

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

    private String determineCategoryByMode(String mode, String context, int questionNumber) {
        if (context != null) {
            String ctx = context.toLowerCase();
            if (ctx.contains("自我介绍") || ctx.contains("介绍自己") || ctx.contains("开场")) return "自我介绍";
            if (ctx.contains("技能") || ctx.contains("专业") || ctx.contains("技术") || ctx.contains("能力")) return "专业技能";
            if (ctx.contains("项目") || ctx.contains("经验") || ctx.contains("案例")) return "项目经验";
            if (ctx.contains("情景") || ctx.contains("场景") || ctx.contains("如果") || ctx.contains("假设")) return "情景分析";
            if (ctx.contains("行为") || ctx.contains("素质") || ctx.contains("冲突")) return "行为面试";
            if (ctx.contains("规划") || ctx.contains("目标") || ctx.contains("未来") || ctx.contains("职业")) return "职业规划";
        }
        List<String> phases = modeService.getPhaseSequence(mode);
        return phases.get((questionNumber - 1) % phases.size());
    }

    private String determineDifficultyByMode(String mode, String context, String sessionId, int questionNumber) {
        if (context != null) {
            if (context.contains("高级") || context.contains("hard") || context.contains("深入")) return "hard";
            if (context.contains("初级") || context.contains("easy") || context.contains("入门")) return "easy";
        }
        Double avg = sessionId != null ? fetchAverageScore(sessionId) : null;
        if (avg != null && avg.isNaN()) {
            avg = null;
        }
        return modeService.determineDifficulty(mode, avg, questionNumber);
    }

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
