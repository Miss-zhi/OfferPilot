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

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockInterviewTool {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "session[=:]([a-zA-Z0-9_-]+)");

    private static final Map<String, List<String>> QUESTION_BANK = Map.of(
            "Java基础", List.of(
                    "请解释Java中HashMap的实现原理",
                    "谈谈Java内存模型（JMM）的理解",
                    "什么是Java的垃圾回收机制？常见GC算法有哪些？",
                    "synchronized和ReentrantLock有什么区别？"
            ),
            "并发编程", List.of(
                    "解释线程池的工作原理和核心参数",
                    "什么是CAS操作？它在Java中如何实现？",
                    "谈谈volatile关键字的作用和原理",
                    "如何避免死锁？举例说明"
            ),
            "数据库", List.of(
                    "MySQL索引的底层数据结构是什么？为什么用B+树？",
                    "解释事务的ACID特性和隔离级别",
                    "什么是慢查询？如何优化？",
                    "分库分表的策略有哪些？各有什么优缺点？"
            ),
            "系统设计", List.of(
                    "设计一个短链接服务，需要考虑哪些方面？",
                    "如何设计一个高并发的秒杀系统？",
                    "谈谈你对微服务架构的理解",
                    "如何保证分布式系统的一致性？"
            ),
            "通用", List.of(
                    "请做一个自我介绍",
                    "你为什么选择我们公司？",
                    "谈谈你的职业规划",
                    "你最大的优点和缺点是什么？"
            )
    );

    private static final List<String> CATEGORIES = List.of(
            "Java基础", "并发编程", "数据库", "系统设计", "通用");

    private final InterviewQuestionRepository questionRepo;
    private final InterviewSessionRepository sessionRepo;

    @Tool(name = "generate_next_question", description = "根据当前面试上下文生成下一个模拟面试问题，支持渐进式难度调整")
    public NextQuestionResult generateNextQuestion(
            @ToolParam(name = "context", description = "当前面试上下文，包括已有问答历史和面试方向") String context) {
        log.info("generate_next_question called: contextLen={}", context != null ? context.length() : 0);

        String sessionId = parseSessionId(context);
        String category = determineCategory(context);
        String difficulty = determineDifficulty(context, sessionId);
        String question = selectQuestion(category, context);
        String reason = buildReason(category, difficulty, context);

        persistQuestion(sessionId, question, category, difficulty);

        log.info("generate_next_question result: sessionId={}, category={}, difficulty={}, question={}",
                sessionId, category, difficulty, question);
        return new NextQuestionResult(question, category, difficulty, reason);
    }

    /**
     * 从 context 字符串中解析 sessionId。
     */
    private String parseSessionId(String context) {
        if (context == null) {
            return null;
        }
        Matcher matcher = SESSION_ID_PATTERN.matcher(context);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 将生成的问题持久化到 InterviewQuestion，并更新 InterviewSession.questionCount。
     */
    private void persistQuestion(String sessionId, String questionText, String category, String difficulty) {
        if (sessionId == null) {
            return;
        }
        try {
            List<InterviewQuestion> existing = questionRepo.findBySessionIdOrderBySortOrder(sessionId);
            InterviewQuestion question = new InterviewQuestion();
            question.setSessionId(sessionId);
            question.setQuestionText(questionText);
            question.setSortOrder(existing.size());
            questionRepo.save(question);

            InterviewSession session = sessionRepo.findBySessionId(sessionId).orElse(null);
            if (session != null) {
                session.setQuestionCount(
                        session.getQuestionCount() != null ? session.getQuestionCount() + 1 : 1);
                sessionRepo.save(session);
            }

            log.info("Question persisted: sessionId={}, sortOrder={}, question={}",
                    sessionId, existing.size(), questionText);
        } catch (Exception e) {
            log.warn("Failed to persist question: sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
    }

    private String determineCategory(String context) {
        if (context == null) {
            return "通用";
        }
        String ctx = context.toLowerCase();
        for (String category : CATEGORIES) {
            if (ctx.contains(category.toLowerCase())) {
                return category;
            }
        }
        if (ctx.contains("java")) {
            return "Java基础";
        }
        if (ctx.contains("并发") || ctx.contains("线程") || ctx.contains("锁")) {
            return "并发编程";
        }
        if (ctx.contains("sql") || ctx.contains("数据库") || ctx.contains("mysql")) {
            return "数据库";
        }
        if (ctx.contains("设计") || ctx.contains("架构") || ctx.contains("系统")) {
            return "系统设计";
        }
        return "通用";
    }

    /**
     * 基于已有问答得分确定下一题难度（渐进式：高分→提升难度，低分→同难度巩固）。
     * 优先使用 DB 中的实际得分，无 sessionId 时回退为上下文分析。
     */
    private String determineDifficulty(String context, String sessionId) {
        if (context == null) {
            return "medium";
        }
        if (context.contains("高级") || context.contains("hard") || context.contains("深入")) {
            return "hard";
        }
        if (context.contains("初级") || context.contains("easy") || context.contains("入门")) {
            return "easy";
        }

        if (sessionId != null) {
            return determineDifficultyFromScores(sessionId);
        }

        int questionCount = countPreviousQuestions(context);
        if (questionCount > 5) {
            return "hard";
        }
        if (questionCount > 2) {
            return "medium";
        }
        return "easy";
    }

    /**
     * 根据已有 InterviewQuestion 的实际得分决定下一题难度。
     */
    private String determineDifficultyFromScores(String sessionId) {
        try {
            List<InterviewQuestion> existing = questionRepo.findBySessionIdOrderBySortOrder(sessionId);
            if (existing.isEmpty()) {
                return "easy";
            }
            double avgScore = existing.stream()
                    .filter(q -> q.getTechScore() != null && q.getExprScore() != null && q.getCoverageScore() != null)
                    .mapToDouble(q -> (q.getTechScore() + q.getExprScore() + q.getCoverageScore()) / 3.0)
                    .average()
                    .orElse(50);

            if (avgScore >= 75) {
                return "hard";
            }
            if (avgScore >= 50) {
                return "medium";
            }
            return "easy";
        } catch (Exception e) {
            log.warn("Failed to determine difficulty from scores: {}", e.getMessage());
            return "medium";
        }
    }

    private String selectQuestion(String category, String context) {
        List<String> questions = QUESTION_BANK.getOrDefault(category, QUESTION_BANK.get("通用"));
        int usedCount = countPreviousQuestions(context);
        int index = usedCount % questions.size();
        return questions.get(index);
    }

    private String buildReason(String category, String difficulty, String context) {
        int questionCount = countPreviousQuestions(context);
        return String.format("第%d题，类别：%s，难度：%s，基于当前面试进度自动匹配",
                questionCount + 1, category, difficulty);
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
