/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.AnswerAnalysisResult;
import com.tutorial.offerpilot.entity.InterviewQuestion;
import com.tutorial.offerpilot.repository.InterviewQuestionRepository;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 回答分析工具 — 持久化原始 QA 数据并返回评估指导，由 LLM 据此生成评分和评语。
 *
 * <p>职责划分：
 * <ul>
 *   <li>本工具：保存原始 QA 到 DB、构建评估指导文本</li>
 *   <li>LLM：根据问题和回答内容，自由生成专业深度/表达能力/覆盖度评分及亮点/不足/建议</li>
 * </ul>
 *
 * <p>不再硬编码任何评分规则、关键词匹配或评语模板。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerAnalyzeTool {

    private final InterviewQuestionRepository questionRepo;

    @Tool(name = "analyze_answer",
            description = "保存面试回答并获取评估指导，由你据此分析回答质量并生成评分和评语")
    public AnswerAnalysisResult analyze(
            @ToolParam(name = "question", description = "面试问题原文") String question,
            @ToolParam(name = "answer", description = "用户的回答内容") String answer) {
        log.info("analyze_answer called: questionLen={}, answerLen={}",
                question != null ? question.length() : 0,
                answer != null ? answer.length() : 0);

        if (question == null || answer == null) {
            return new AnswerAnalysisResult(question, answer,
                    "问题和回答均为空，无法分析。", null, null, null,
                    List.of(), List.of(), null);
        }

        persistRawQA(question, answer);
        String guidance = buildGuidance(question, answer);

        log.info("analyze_answer: guidance generated, questionLen={}, answerLen={}",
                question.length(), answer.length());
        return new AnswerAnalysisResult(question, answer, guidance,
                null, null, null, List.of(), List.of(), null);
    }

    /**
     * 构建评估指导文本，指示 LLM 如何分析回答。
     * 不包含任何硬编码评分规则或评语模板。
     */
    private String buildGuidance(String question, String answer) {
        return String.format("""
                请分析以下面试回答的质量：

                【面试问题】%s

                【用户回答】%s

                请从以下三个维度评分（0-100）并给出评语：
                1. 专业深度：回答是否展现了对该领域的深入理解和专业知识
                2. 表达能力：回答结构是否清晰、逻辑是否连贯、语言是否流畅
                3. 内容覆盖度：回答是否从多个角度展开，是否包含了具体案例或论据支撑

                请输出格式：
                - 专业深度：XX分 — [评语]
                - 表达能力：XX分 — [评语]
                - 内容覆盖度：XX分 — [评语]
                - 亮点：[列出1-3个亮点]
                - 不足：[列出1-3个需要改进的地方]
                - 改进建议：[给出具体可操作的改进建议]
                """, question, answer);
    }

    /**
     * 保存原始 QA 数据到 DB（不含评分，评分由 LLM 在对话中生成）。
     */
    private void persistRawQA(String question, String answer) {
        try {
            List<InterviewQuestion> matches = questionRepo.findByQuestionText(question);
            if (matches.isEmpty()) {
                return;
            }
            for (InterviewQuestion q : matches) {
                q.setAnswerText(answer);
                questionRepo.save(q);
            }
            log.info("Raw QA persisted to {} question(s)", matches.size());
        } catch (Exception e) {
            log.warn("Failed to persist raw QA: {}", e.getMessage());
        }
    }
}
