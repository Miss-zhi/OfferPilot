/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.entity.SearchFeedback;
import com.tutorial.offerpilot.entity.SearchToolLog;
import com.tutorial.offerpilot.repository.SearchFeedbackRepository;
import com.tutorial.offerpilot.repository.SearchToolLogRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 搜索分析服务。
 * 负责搜索日志持久化和统计分析，为知识库内容建设提供数据驱动依据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAnalyticsService {

    private final SearchToolLogRepository logRepo;
    private final SearchFeedbackRepository feedbackRepo;

    /**
     * 记录搜索日志。
     */
    public void logSearch(String userId, String queryText, String toolName,
                          int milvusHits, int dbHits, int webHits, long latencyMs) {
        SearchToolLog log = new SearchToolLog();
        log.setUserId(userId);
        log.setQueryText(queryText);
        log.setToolName(toolName);
        log.setMilvusHits(milvusHits);
        log.setDbHits(dbHits);
        log.setWebHits(webHits);
        log.setTotalResults(milvusHits + dbHits + webHits);
        log.setZeroResult(log.getTotalResults() == 0 ? 1 : 0);
        log.setLatencyMs(latencyMs);
        logRepo.save(log);
    }

    /**
     * 记录搜索反馈。
     */
    public void recordFeedback(String userId, String queryText, String toolName,
                               String resultSource, int resultCount, Boolean helpful, String sessionId) {
        SearchFeedback feedback = new SearchFeedback();
        feedback.setUserId(userId);
        feedback.setQueryText(queryText);
        feedback.setToolName(toolName);
        feedback.setResultSource(resultSource);
        feedback.setResultCount(resultCount);
        feedback.setHelpful(helpful);
        feedback.setSessionId(sessionId);
        feedbackRepo.save(feedback);
    }

    /**
     * 获取搜索统计数据。
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Top queries
        List<Object[]> topQueries = logRepo.findTopQueries();
        List<Map<String, Object>> topQueryList = new ArrayList<>();
        int limit = Math.min(topQueries.size(), 10);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("query", topQueries.get(i)[0]);
            entry.put("count", topQueries.get(i)[1]);
            topQueryList.add(entry);
        }
        stats.put("topQueries", topQueryList);

        // Zero result queries
        List<SearchToolLog> zeroLogs = logRepo.findZeroResultLogs();
        Map<String, Integer> zeroResultMap = new LinkedHashMap<>();
        for (SearchToolLog log : zeroLogs) {
            zeroResultMap.merge(log.getQueryText(), 1, Integer::sum);
        }
        List<Map<String, Object>> zeroResultList = new ArrayList<>();
        zeroResultMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("query", e.getKey());
                    entry.put("count", e.getValue());
                    zeroResultList.add(entry);
                });
        stats.put("zeroResultQueries", zeroResultList);

        // Source distribution from recent logs
        List<SearchToolLog> allLogs = logRepo.findAll();
        long totalLogs = allLogs.size();
        if (totalLogs > 0) {
            long kbCount = allLogs.stream().filter(l -> l.getMilvusHits() > 0).count();
            long dbCount = allLogs.stream().filter(l -> l.getDbHits() > 0).count();
            long webCount = allLogs.stream().filter(l -> l.getWebHits() > 0).count();
            Map<String, Double> sourceDist = new LinkedHashMap<>();
            sourceDist.put("kb", totalLogs > 0 ? (double) kbCount / totalLogs : 0);
            sourceDist.put("db", totalLogs > 0 ? (double) dbCount / totalLogs : 0);
            sourceDist.put("web", totalLogs > 0 ? (double) webCount / totalLogs : 0);
            stats.put("sourceDistribution", sourceDist);
        }

        stats.put("totalSearches", totalLogs);

        // Average latency
        double avgLatency = allLogs.stream()
                .mapToLong(SearchToolLog::getLatencyMs)
                .average()
                .orElse(0);
        stats.put("avgLatencyMs", Math.round(avgLatency));

        return stats;
    }
}
