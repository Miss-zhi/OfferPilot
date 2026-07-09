/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.AnswerAnalysisResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import com.tutorial.offerpilot.service.InterviewAnalysisService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerAnalyzeTool {

    private static final Pattern TECH_KEYWORD = Pattern.compile(
            "算法|数据结构|设计模式|框架|架构|数据库|缓存|并发|分布式|微服务|API|协议",
            Pattern.CASE_INSENSITIVE);

    private final InterviewAnalysisService analysisService;
    private final InterviewQuestionRepository questionRepo;

    @Tool(name = "analyze_answer", description = "分析面试回答质量，评估回答的完整性、准确性和表达能力")
    public AnswerAnalysisResult analyze(
            @ToolParam(name = "question", description = "面试问题原文") String question,
            @ToolParam(name = "answer", description = "用户的回答内容") String answer) {
        log.info("analyze_answer called: questionLen={}, answerLen={}",
                question != null ? question.length() : 0,
                answer != null ? answer.length() : 0);

        if (question == null || answer == null) {
            return new AnswerAnalysisResult(0, 0, 0, List.of(), List.of("问题或回答为空"), "请提供完整的问题和回答");
        }

        int techScore = evaluateTechScore(answer);
        int exprScore = evaluateExpression(answer);
        int coverageScore = evaluateCoverage(question, answer);
        List<String> highlights = extractHighlights(answer, techScore, exprScore);
        List<String> weaknesses = extractWeaknesses(answer, techScore, exprScore, coverageScore);
        String suggestion = generateSuggestion(techScore, exprScore, coverageScore);

        persistAnalysis(question, answer, techScore, exprScore, coverageScore, highlights, weaknesses);

        log.info("analyze_answer result: techScore={}, exprScore={}, coverageScore={}",
                techScore, exprScore, coverageScore);
        return new AnswerAnalysisResult(techScore, exprScore, coverageScore, highlights, weaknesses, suggestion);
    }

    /**
     * Best-effort 持久化：查找匹配问题文本的 InterviewQuestion 记录并更新评分。
     */
    private void persistAnalysis(String question, String answer, int techScore, int exprScore,
                                 int coverageScore, List<String> highlights, List<String> weaknesses) {
        try {
            List<InterviewQuestion> matches = questionRepo.findByQuestionText(question);
            if (matches.isEmpty()) {
                return;
            }
            for (InterviewQuestion q : matches) {
                q.setAnswerText(answer);
                q.setTechScore(techScore);
                q.setExprScore(exprScore);
                q.setCoverageScore(coverageScore);
                q.setHighlights(String.join(",", highlights));
                q.setWeaknesses(String.join(",", weaknesses));
                questionRepo.save(q);

                analysisService.saveAnalysis(q.getSessionId(), question, answer,
                        techScore, exprScore, coverageScore,
                        String.join(",", highlights), String.join(",", weaknesses));
            }
            log.info("Analysis persisted to {} question(s)", matches.size());
        } catch (Exception e) {
            log.warn("Failed to persist analysis for question='{}': {}",
                    question, e.getMessage());
        }
    }

    private int evaluateTechScore(String answer) {
        long keywordMatches = TECH_KEYWORD.matcher(answer).results().count();
        int len = answer.length();
        if (len < 50) {
            return 30;
        }
        int base = 40 + (int) Math.min(keywordMatches * 8, 40);
        if (len > 200) {
            base += 10;
        }
        if (len > 500) {
            base += 10;
        }
        return Math.min(base, 100);
    }

    private int evaluateExpression(String answer) {
        int len = answer.length();
        if (len < 30) {
            return 20;
        }
        int score = 30 + (int) Math.min(len / 10.0, 50);
        if (answer.contains("首先") || answer.contains("第一")) {
            score += 10;
        }
        if (answer.contains("总结") || answer.contains("总之") || answer.contains("因此")) {
            score += 10;
        }
        return Math.min(score, 100);
    }

    private int evaluateCoverage(String question, String answer) {
        int len = answer.length();
        if (len < 30) {
            return 20;
        }
        int score = 30 + (int) Math.min(len / 15.0, 50);
        if (answer.contains("例如") || answer.contains("比如") || answer.contains("举例")) {
            score += 10;
        }
        if (answer.contains("优点") && answer.contains("缺点")) {
            score += 10;
        }
        return Math.min(score, 100);
    }

    private List<String> extractHighlights(String answer, int techScore, int exprScore) {
        List<String> highlights = new ArrayList<>();
        if (techScore >= 70) {
            highlights.add("技术关键词覆盖较好");
        }
        if (exprScore >= 70) {
            highlights.add("表达结构清晰，逻辑性强");
        }
        if (answer.length() > 200) {
            highlights.add("回答内容详实，有充分展开");
        }
        if (answer.contains("例如") || answer.contains("比如")) {
            highlights.add("使用了具体案例进行说明");
        }
        return highlights;
    }

    private List<String> extractWeaknesses(String answer, int techScore, int exprScore, int coverageScore) {
        List<String> weaknesses = new ArrayList<>();
        if (techScore < 50) {
            weaknesses.add("技术关键词使用不足，建议补充更多专业术语");
        }
        if (exprScore < 50) {
            weaknesses.add("表达结构不够清晰，建议使用总分总结构");
        }
        if (coverageScore < 50) {
            weaknesses.add("回答覆盖面不足，建议多角度展开");
        }
        if (answer.length() < 50) {
            weaknesses.add("回答过于简短，需要补充更多细节");
        }
        return weaknesses;
    }

    private String generateSuggestion(int techScore, int exprScore, int coverageScore) {
        int avg = (techScore + exprScore + coverageScore) / 3;
        if (avg >= 80) {
            return "回答质量优秀，继续保持。可以尝试增加更多的行业案例来进一步提升说服力。";
        }
        if (avg >= 60) {
            return "回答质量良好。建议在技术深度和表达结构上进一步加强，多使用专业术语和具体案例。";
        }
        return "回答需要较大提升。建议先夯实基础知识，学习STAR法则组织回答结构，并进行更多模拟练习。";
    }
}
