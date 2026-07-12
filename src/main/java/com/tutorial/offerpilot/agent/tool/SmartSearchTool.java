/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.*;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import com.tutorial.offerpilot.service.QueryExpansionService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 统一智能搜索工具。
 * 提供单一 smart_search 入口，内部自动完成意图分类、Query 扩展和多路召回。
 *
 * <p>替代场景：当 LLM 不确定该调用哪个具体的 search_* 工具时，可优先使用此工具，
 * 由后端根据 query 内容自动路由到最优搜索策略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartSearchTool {

    private final KnowledgeBaseService kbService;

    @Autowired(required = false)
    private QueryExpansionService queryExpansionService;

    @Tool(name = "smart_search", description = "统一智能搜索：根据自然语言查询自动识别意图并检索面试题库、答案库、公司面经和学习资源")
    public SmartSearchResult smartSearch(
            @ToolParam(name = "query", description = "用户原始查询（自然语言），如 '帮我找字节跳动后端面试题'") String query,
            @ToolParam(name = "context", description = "搜索上下文 JSON（可选）：{sessionId, role, stage}", required = false) String context) {
        log.info("smart_search called: query={}, context={}", query, context);
        long start = System.currentTimeMillis();

        // 1. 意图分类
        String intent = classifyIntent(query);

        // 2. Query 扩展
        List<String> expandedQueries = expandQuery(query);

        // 3. 多路召回
        SearchRequest req = new SearchRequest();
        req.setKeywords(query);
        req.setTopK(10);
        List<SmartSearchResult.SmartSearchItem> items = new ArrayList<>();

        // 根据意图路由到不同检索目标
        switch (intent) {
            case "company" -> {
                CompanySearchResult companyResult = kbService.searchCompanyInterviews(query);
                for (CompanySearchResult.CompanyItem ci : companyResult.getCompanies()) {
                    SmartSearchResult.SmartSearchItem item = new SmartSearchResult.SmartSearchItem();
                    item.setType("company_interview");
                    item.setTitle(ci.getCompanyName() + " - " + ci.getInterviewType());
                    item.setSnippet(ci.getSummary());
                    item.setCategory("公司调研");
                    item.setDifficulty(ci.getDifficulty());
                    item.setRelevanceScore(ci.getRelevanceScore());
                    item.setSource(ci.getSource());
                    item.setCompanyName(ci.getCompanyName());
                    items.add(item);
                }
            }
            case "practice" -> {
                QuestionSearchResult questionResult = kbService.searchQuestions(query);
                for (QuestionSearchResult.QuestionItem qi : questionResult.getQuestions()) {
                    SmartSearchResult.SmartSearchItem item = new SmartSearchResult.SmartSearchItem();
                    item.setType("question");
                    item.setTitle(qi.getContent());
                    item.setSnippet(qi.getContent());
                    item.setCategory(qi.getCategory());
                    item.setRelevanceScore(qi.getRelevanceScore());
                    item.setSource(qi.getSource());
                    items.add(item);
                }
            }
            case "learn" -> {
                ResourceListResult resourceResult = kbService.searchResources(query);
                for (ResourceListResult.ResourceItem ri : resourceResult.getResources()) {
                    SmartSearchResult.SmartSearchItem item = new SmartSearchResult.SmartSearchItem();
                    item.setType("resource");
                    item.setTitle(ri.getTitle());
                    item.setSnippet(ri.getTitle());
                    item.setCategory(ri.getType());
                    item.setRelevanceScore(ri.getRelevanceScore());
                    item.setSource(ri.getSource());
                    item.setUrl(ri.getUrl());
                    items.add(item);
                }
            }
            default -> {
                // general: 并行搜索所有类型
                QuestionSearchResult qr = kbService.searchQuestions(query);
                ResourceListResult rr = kbService.searchResources(query);
                for (var qi : qr.getQuestions()) {
                    SmartSearchResult.SmartSearchItem item = new SmartSearchResult.SmartSearchItem();
                    item.setType("question");
                    item.setTitle(qi.getContent());
                    item.setSnippet(qi.getContent());
                    item.setCategory(qi.getCategory());
                    item.setRelevanceScore(qi.getRelevanceScore());
                    item.setSource(qi.getSource());
                    items.add(item);
                }
                for (var ri : rr.getResources()) {
                    SmartSearchResult.SmartSearchItem item = new SmartSearchResult.SmartSearchItem();
                    item.setType("resource");
                    item.setTitle(ri.getTitle());
                    item.setSnippet(ri.getTitle());
                    item.setCategory(ri.getType());
                    item.setRelevanceScore(ri.getRelevanceScore());
                    item.setSource(ri.getSource());
                    item.setUrl(ri.getUrl());
                    items.add(item);
                }
            }
        }

        // 4. 按相关性排序
        items.sort((a, b) -> Float.compare(
                b.getRelevanceScore() != null ? b.getRelevanceScore() : 0f,
                a.getRelevanceScore() != null ? a.getRelevanceScore() : 0f));

        // 5. 去重并限制数量
        Set<String> seen = new HashSet<>();
        List<SmartSearchResult.SmartSearchItem> uniqueItems = new ArrayList<>();
        for (SmartSearchResult.SmartSearchItem item : items) {
            if (seen.add(item.getTitle())) {
                uniqueItems.add(item);
                if (uniqueItems.size() >= 15) break;
            }
        }

        long latencyMs = System.currentTimeMillis() - start;

        SmartSearchResult result = new SmartSearchResult();
        result.setIntent(intent);
        result.setExpandedQueries(expandedQueries);
        result.setTotal(uniqueItems.size());
        result.setLatencyMs(latencyMs);
        result.setPrimarySource(detectPrimarySource(uniqueItems));
        result.setItems(uniqueItems);

        log.info("smart_search completed: intent={}, results={}, latencyMs={}", intent, uniqueItems.size(), latencyMs);
        return result;
    }

    /**
     * 轻量规则意图分类。
     */
    private String classifyIntent(String query) {
        if (query == null) return "general";
        String lower = query.toLowerCase();

        // 公司+面试关键词 → company
        if ((lower.contains("公司") || lower.contains("企业"))
                && (lower.contains("面试") || lower.contains("面经") || lower.contains("招聘"))) {
            return "company";
        }

        // 出题/刷题关键词 → practice
        if (lower.contains("出题") || lower.contains("刷题") || lower.contains("练习")
                || lower.contains("mock") || lower.contains("模拟面试")) {
            return "practice";
        }

        // 学习/教程关键词 → learn
        if (lower.contains("学习") || lower.contains("教程") || lower.contains("资料")
                || lower.contains("文档") || lower.contains("怎么学") || lower.contains("入门")) {
            return "learn";
        }

        // 面试题相关 → practice
        if (lower.contains("面试题") || lower.contains("考题") || lower.contains("hr面")) {
            return "practice";
        }

        return "general";
    }

    private List<String> expandQuery(String query) {
        if (queryExpansionService != null) {
            return queryExpansionService.expand(query);
        }
        return List.of(query);
    }

    private String detectPrimarySource(List<SmartSearchResult.SmartSearchItem> items) {
        if (items.isEmpty()) return "none";
        long kbCount = items.stream().filter(i -> "kb".equals(i.getSource())).count();
        long dbCount = items.stream().filter(i -> "db".equals(i.getSource())).count();
        long webCount = items.stream().filter(i -> "web".equals(i.getSource())).count();
        if (kbCount >= dbCount && kbCount >= webCount) return "kb";
        if (dbCount >= webCount) return "db";
        return "web";
    }
}
