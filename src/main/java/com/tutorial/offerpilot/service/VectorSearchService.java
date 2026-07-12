/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus 向量检索服务。
 * 负责在向量数据库中执行相似度检索，支持单 Collection 和多 Collection 搜索。
 */
@Slf4j
@Service
public class VectorSearchService {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingService embeddingService;

    public VectorSearchService(MilvusClientV2 milvusClient, EmbeddingService embeddingService) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
    }

    private static final List<String> OUTPUT_FIELDS = List.of("doc_id", "chunk_index", "content", "category", "difficulty", "position");

    /** RRF 融合常量，标准值 60 */
    private static final int RRF_K = 60;

    /**
     * 将 Milvus COSINE 距离转换为归一化相关性分数。
     *
     * <p>COSINE 距离 d ∈ [0, 2]，映射到 [0, 1]，1=完全匹配。
     * 公式: score = 1 - d/2，等价于 (1 + cosine_similarity) / 2。
     *
     * @param distance Milvus COSINE 距离
     * @return 归一化相关性分数，范围 [0, 1]
     */
    public static float cosineDistanceToScore(float distance) {
        return Math.max(0f, Math.min(1f, 1f - distance / 2f));
    }

    /**
     * 在指定 Collection 中执行向量检索。
     *
     * @param collectionName Milvus Collection 名称
     * @param query          查询文本
     * @param topK           返回 Top-K 个最相似结果
     * @return 检索结果列表，按相似度降序排列
     */
    public List<SearchTestResponse.SearchHit> search(String collectionName, String query, int topK) {
        return search(collectionName, query, topK, null);
    }

    /**
     * 在指定 Collection 中执行向量检索（支持元数据过滤）。
     *
     * @param collectionName Milvus Collection 名称
     * @param query          查询文本
     * @param topK           返回 Top-K 个最相似结果
     * @param filterExpr     Milvus 标量过滤表达式，如 "category == \"专业技能\""，为 null 时不过滤
     * @return 检索结果列表，按相似度降序排列
     */
    public List<SearchTestResponse.SearchHit> search(String collectionName, String query, int topK, String filterExpr) {
        float[] queryVector = embeddingService.embed(query);
        return searchByVector(collectionName, queryVector, topK, filterExpr);
    }

    /**
     * 在多个 Collection 中执行向量检索并合并排序。
     * 适用于多租户场景（同时检索公共库 + 用户私有库）。
     *
     * @param collectionNames 多个 Milvus Collection 名称
     * @param query           查询文本
     * @param topK            每个 Collection 返回的候选数量
     * @param finalTopK       合并后的最终 Top-K
     * @return 检索结果列表，按相似度降序排列
     */
    public List<SearchTestResponse.SearchHit> searchMultiCollection(
            List<String> collectionNames, String query, int topK, int finalTopK) {
        return searchMultiCollection(collectionNames, query, topK, finalTopK, null);
    }

    /**
     * 在多个 Collection 中执行向量检索并合并排序（支持元数据过滤）。
     *
     * @param collectionNames 多个 Milvus Collection 名称
     * @param query           查询文本
     * @param topK            每个 Collection 返回的候选数量
     * @param finalTopK       合并后的最终 Top-K
     * @param filterExpr      Milvus 标量过滤表达式，为 null 时不过滤
     * @return 检索结果列表，按相似度降序排列
     */
    public List<SearchTestResponse.SearchHit> searchMultiCollection(
            List<String> collectionNames, String query, int topK, int finalTopK, String filterExpr) {
        if (collectionNames == null || collectionNames.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingService.embed(query);

        List<SearchTestResponse.SearchHit> allHits = new ArrayList<>();
        for (String collection : collectionNames) {
            try {
                List<SearchTestResponse.SearchHit> hits = searchByVector(collection, queryVector, topK, filterExpr);
                allHits.addAll(hits);
            } catch (Exception e) {
                log.warn("Search failed for collection {}: {}", collection, e.getMessage());
            }
        }

        // 按归一化分数降序排列（1=最相似），取最终 Top-K
        allHits.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        int limit = Math.min(finalTopK, allHits.size());
        return allHits.subList(0, limit);
    }

    /**
     * Reciprocal Rank Fusion (RRF) 多路召回融合。
     * 使用排名位置而非原始分数进行融合，不受各路 score 尺度差异影响。
     *
     * <p>RRF 公式: score(d) = Σ 1 / (k + rank_i(d))，其中 rank 从 1 开始。
     *
     * @param pathResults 多路召回结果列表，每路内部已按相关性排序（best first）
     * @param k           RRF 常数，通常取 60
     * @return 融合去重后的结果列表，按 RRF score 降序排列
     */
    public static List<SearchTestResponse.SearchHit> fuseWithRRF(
            List<List<SearchTestResponse.SearchHit>> pathResults, int k) {
        if (pathResults == null || pathResults.isEmpty()) {
            return List.of();
        }

        // docId + chunkIndex 作为去重 key
        Map<String, SearchTestResponse.SearchHit> bestHit = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new LinkedHashMap<>();

        for (List<SearchTestResponse.SearchHit> path : pathResults) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            for (int rank = 0; rank < path.size(); rank++) {
                SearchTestResponse.SearchHit hit = path.get(rank);
                String key = (hit.getDocId() != null ? hit.getDocId() : "") + "::" + hit.getChunkIndex();

                double rrfScore = 1.0 / (k + rank + 1);
                rrfScores.merge(key, rrfScore, Double::sum);

                // 保留分数最高的那个 hit（通常来自第一路）
                if (!bestHit.containsKey(key)) {
                    bestHit.put(key, hit);
                }
            }
        }

        // 按 RRF score 降序排列
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(rrfScores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<SearchTestResponse.SearchHit> results = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sorted) {
            SearchTestResponse.SearchHit hit = bestHit.get(entry.getKey());
            if (hit != null) {
                // 用 RRF score 替换原始 score 作为融合后的相关性分数
                hit.setScore(entry.getValue().floatValue());
                results.add(hit);
            }
        }

        return results;
    }

    /**
     * RRF 融合的便捷方法，使用默认 k=60。
     */
    public static List<SearchTestResponse.SearchHit> fuseWithRRF(
            List<List<SearchTestResponse.SearchHit>> pathResults) {
        return fuseWithRRF(pathResults, RRF_K);
    }

    /**
     * 使用向量直接检索（核心方法）。
     */
    private List<SearchTestResponse.SearchHit> searchByVector(String collectionName, float[] vector, int topK) {
        return searchByVector(collectionName, vector, topK, null);
    }

    /**
     * 使用向量直接检索（核心方法，支持元数据过滤）。
     */
    private List<SearchTestResponse.SearchHit> searchByVector(String collectionName, float[] vector, int topK, String filterExpr) {
        List<List<Float>> vectors = List.of(toFloatList(vector));

        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField("vector")
                .data(vectors)
                .topK(topK)
                .outputFields(OUTPUT_FIELDS);

        boolean hasFilter = filterExpr != null && !filterExpr.isBlank();
        if (hasFilter) {
            builder.filter(filterExpr);
        }

        SearchReq searchReq = builder.build();

        SearchResp searchResp;
        try {
            searchResp = milvusClient.search(searchReq);
        } catch (Exception e) {
            // 旧 Collection 缺少新标量字段（category/difficulty/position）时降级为无过滤检索
            if (hasFilter) {
                log.warn("Filtered search failed for collection {} (likely missing scalar fields), retrying without filter: {}",
                        collectionName, e.getMessage());
                return searchByVector(collectionName, vector, topK, null);
            }
            throw new RuntimeException("Vector search failed for collection " + collectionName, e);
        }

        return extractHits(searchResp, collectionName, topK);
    }

    /**
     * 从 Milvus 搜索结果中提取 SearchHit 列表。
     */
    private List<SearchTestResponse.SearchHit> extractHits(SearchResp searchResp, String collectionName, int topK) {
        List<SearchTestResponse.SearchHit> hits = new ArrayList<>();
        if (searchResp.getSearchResults() == null) {
            return hits;
        }

        for (List<SearchResp.SearchResult> resultList : searchResp.getSearchResults()) {
            for (SearchResp.SearchResult result : resultList) {
                Map<String, Object> entity = result.getEntity();
                if (entity == null) {
                    continue;
                }

                SearchTestResponse.SearchHit hit = new SearchTestResponse.SearchHit();
                hit.setDocId(toString(entity.get("doc_id")));
                hit.setChunkIndex(toInt(entity.get("chunk_index")));
                hit.setContent(toString(entity.get("content")));
                hit.setScore(cosineDistanceToScore(result.getDistance()));
                hits.add(hit);
            }
        }

        log.debug("Vector search: collection={}, topK={}, results={}", collectionName, topK, hits.size());
        return hits;
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    private String toString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private Integer toInt(Object obj) {
        if (obj instanceof Number num) {
            return num.intValue();
        }
        if (obj instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
