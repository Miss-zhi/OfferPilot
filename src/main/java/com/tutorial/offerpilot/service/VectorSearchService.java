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

    private static final List<String> OUTPUT_FIELDS = List.of("doc_id", "chunk_index", "content");

    /**
     * 在指定 Collection 中执行向量检索。
     *
     * @param collectionName Milvus Collection 名称
     * @param query          查询文本
     * @param topK           返回 Top-K 个最相似结果
     * @return 检索结果列表，按相似度降序排列
     */
    public List<SearchTestResponse.SearchHit> search(String collectionName, String query, int topK) {
        float[] queryVector = embeddingService.embed(query);
        return searchByVector(collectionName, queryVector, topK);
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
        if (collectionNames == null || collectionNames.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingService.embed(query);

        List<SearchTestResponse.SearchHit> allHits = new ArrayList<>();
        for (String collection : collectionNames) {
            try {
                List<SearchTestResponse.SearchHit> hits = searchByVector(collection, queryVector, topK);
                allHits.addAll(hits);
            } catch (Exception e) {
                log.warn("Search failed for collection {}: {}", collection, e.getMessage());
            }
        }

        // 按距离升序排列（Milvus 距离越小越相似），取最终 Top-K
        allHits.sort((a, b) -> Float.compare(a.getScore(), b.getScore()));
        int limit = Math.min(finalTopK, allHits.size());
        return allHits.subList(0, limit);
    }

    /**
     * 使用向量直接检索（核心方法）。
     */
    private List<SearchTestResponse.SearchHit> searchByVector(String collectionName, float[] vector, int topK) {
        List<List<Float>> vectors = List.of(toFloatList(vector));

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .annsField("vector")
                .data(vectors)
                .topK(topK)
                .outputFields(OUTPUT_FIELDS)
                .build();

        SearchResp searchResp = milvusClient.search(searchReq);

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
                hit.setScore(result.getDistance());
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
