/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.AnswerSearchResult;
import com.tutorial.offerpilot.dto.tool.KnowledgeGapResult;
import com.tutorial.offerpilot.dto.tool.SearchRequest;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识盲区检测工具 — RAG 检索标准答案，词法对比标记遗漏知识点。
 * 返回词法层面的对比数据 + LLM 语义复核指导，最终评语由 LLM 生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeGapTool {

    private final KnowledgeBaseService kbService;

    /** 提取术语的正则：引号内术语 或 以原理/模式/算法等结尾的专有名词 */
    private static final Pattern TERM_PATTERN = Pattern.compile(
            "(?:[\\u201c\\u201d\"])?([\\u4e00-\\u9fa5a-zA-Z]{2,20})(?:[\\u201c\\u201d\"])?"
                    + "|([\\u4e00-\\u9fa5a-zA-Z]+(?:原理|模式|算法|协议|定律|架构|模型|思想|机制|策略))");

    @Tool(name = "detect_knowledge_gaps",
            description = "RAG检索标准答案并对比用户回答，标记遗漏的知识点和概念")
    public KnowledgeGapResult detectGaps(
            @ToolParam(name = "question", description = "面试问题") String question,
            @ToolParam(name = "user_answer", description = "用户回答文本") String userAnswer,
            @ToolParam(name = "top_k", description = "检索标答数量（可选），默认3", required = false) Integer topK) {

        log.info("detect_knowledge_gaps called: questionLen={}, answerLen={}",
                question != null ? question.length() : 0,
                userAnswer != null ? userAnswer.length() : 0);

        if (question == null || question.isBlank()) {
            return new KnowledgeGapResult("问题为空，无法检测知识盲区", List.of(), List.of(), 0);
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            return new KnowledgeGapResult("用户回答为空，无法检测知识盲区", List.of(), List.of(), 0);
        }

        // 1. RAG 检索标准答案
        int k = topK != null ? Math.min(topK, 10) : 3;
        SearchRequest req = new SearchRequest();
        req.setKeywords(question);
        req.setTopK(k);
        AnswerSearchResult standardAnswers = kbService.searchAnswers(req);

        // 2. 提取标答中的关键知识点/概念
        Set<String> standardConcepts = extractConcepts(standardAnswers);

        // 3. 提取用户回答中的知识点/概念
        Set<String> userConcepts = extractConceptsFromText(userAnswer);

        // 4. 计算遗漏
        Set<String> missing = new LinkedHashSet<>(standardConcepts);
        missing.removeAll(userConcepts);

        Set<String> covered = new LinkedHashSet<>(standardConcepts);
        covered.retainAll(userConcepts);

        int coverageRate = standardConcepts.isEmpty() ? 0
                : (int) Math.round((double) covered.size() / standardConcepts.size() * 100);

        String guidance = buildGapGuidance(covered, missing, coverageRate);

        log.info("detect_knowledge_gaps: standardConcepts={}, userConcepts={}, covered={}, missing={}, rate={}%",
                standardConcepts.size(), userConcepts.size(), covered.size(), missing.size(), coverageRate);

        return new KnowledgeGapResult(guidance,
                new ArrayList<>(covered), new ArrayList<>(missing), coverageRate);
    }

    /**
     * 从 RAG 检索结果中提取关键术语。
     * AnswerSearchResult.AnswerItem 的答案文本字段是 {@code answer}。
     */
    private Set<String> extractConcepts(AnswerSearchResult result) {
        Set<String> concepts = new LinkedHashSet<>();
        if (result == null || result.getAnswers() == null) {
            return concepts;
        }
        for (AnswerSearchResult.AnswerItem ans : result.getAnswers()) {
            String text = ans.getAnswer();
            if (text == null || text.isBlank()) {
                continue;
            }
            concepts.addAll(matchTerms(text));
        }
        return concepts;
    }

    /** 从纯文本中提取关键术语。 */
    private Set<String> extractConceptsFromText(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashSet<>();
        }
        return matchTerms(text);
    }

    /** 使用正则从文本中匹配术语。 */
    private Set<String> matchTerms(String text) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher m = TERM_PATTERN.matcher(text);
        while (m.find()) {
            String term = m.group(1) != null ? m.group(1) : m.group(2);
            if (term != null && term.length() >= 2) {
                terms.add(term.trim());
            }
        }
        return terms;
    }

    /**
     * 构建知识盲区检测的 LLM 指导。
     * 词法对比结果作为辅助指标，LLM 需做语义级别判断。
     */
    private String buildGapGuidance(Set<String> covered, Set<String> missing, int coverageRate) {
        return String.format("""
                请基于以下词法对比数据进行知识盲区分析：
                - 词法覆盖率：%d%%
                - 词法匹配到的知识点：%s
                - 词法遗漏的知识点：%s

                注意：词法对比仅检测表面术语匹配，你需要从语义层面复核：
                1. 遗漏点中是否有被用户用不同措辞覆盖的内容？
                2. 匹配点中是否有仅提及名称但未深入的内容？
                3. 最终确定真正的知识盲区并给出补充学习建议。""",
                coverageRate, covered.isEmpty() ? "无" : covered,
                missing.isEmpty() ? "无" : missing);
    }
}
