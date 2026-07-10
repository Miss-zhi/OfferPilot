/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;

@DisplayName("VectorSearchService 集成测试")
class VectorSearchServiceIT extends AbstractServiceIT {

    @Autowired
    private VectorSearchService vectorSearchService;

    @MockBean
    private EmbeddingService embeddingService;

    private static final float[] SAMPLE_VECTOR = {0.1f, 0.2f, 0.3f};

    @BeforeEach
    void resetMocks() {
        reset(milvusClient, embeddingService);
    }

    // ==================== search (single collection) ====================

    @Nested
    @DisplayName("search (单Collection)")
    class SearchSingleTests {

        @Test
        @DisplayName("正常检索 → 返回 SearchHit 列表")
        void search_shouldReturnHits() {
            when(embeddingService.embed("Java面试")).thenReturn(SAMPLE_VECTOR);
            when(milvusClient.search(any(SearchReq.class))).thenReturn(buildSearchResp(List.of(
                    hit("doc-001", 0, "HashMap 底层原理", 0.15f),
                    hit("doc-002", 1, "ConcurrentHashMap", 0.25f)
            )));

            List<SearchTestResponse.SearchHit> results = vectorSearchService.search(
                    "kb_test", "Java面试", 5);

            assertEquals(2, results.size());
            assertEquals("doc-001", results.get(0).getDocId());
            assertEquals("HashMap 底层原理", results.get(0).getContent());
            assertEquals(0.15f, results.get(0).getScore(), 0.001);
        }

        @Test
        @DisplayName("空结果 → 返回空列表")
        void search_emptyResults_shouldReturnEmptyList() {
            when(embeddingService.embed("NoResults")).thenReturn(SAMPLE_VECTOR);
            when(milvusClient.search(any(SearchReq.class))).thenReturn(buildSearchResp(List.of()));

            List<SearchTestResponse.SearchHit> results = vectorSearchService.search(
                    "kb_empty", "NoResults", 5);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("searchResp.getSearchResults() 为 null → 返回空列表")
        void search_nullResults_shouldReturnEmptyList() {
            when(embeddingService.embed("test")).thenReturn(SAMPLE_VECTOR);
            SearchResp resp = SearchResp.builder().searchResults(null).build();
            when(milvusClient.search(any(SearchReq.class))).thenReturn(resp);

            List<SearchTestResponse.SearchHit> results = vectorSearchService.search(
                    "kb_null", "test", 5);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("传入正确的 topK 参数")
        void search_shouldUseCorrectTopK() {
            when(embeddingService.embed(anyString())).thenReturn(SAMPLE_VECTOR);
            when(milvusClient.search(any(SearchReq.class))).thenReturn(buildSearchResp(List.of()));

            vectorSearchService.search("kb_001", "query", 10);

            verify(milvusClient).search(argThat(req -> req.getTopK() == 10));
        }
    }

    // ==================== searchMultiCollection ====================

    @Nested
    @DisplayName("searchMultiCollection (多Collection)")
    class SearchMultiTests {

        @Test
        @DisplayName("多个 Collection → 合并排序返回 Top-K")
        void searchMulti_shouldMergeAndSort() {
            when(embeddingService.embed("多库检索")).thenReturn(SAMPLE_VECTOR);
            when(milvusClient.search(any(SearchReq.class)))
                    .thenReturn(buildSearchResp(List.of(
                            hit("doc-A", 0, "Result A", 0.5f)
                    )))
                    .thenReturn(buildSearchResp(List.of(
                            hit("doc-B", 0, "Result B", 0.1f)
                    )));

            List<SearchTestResponse.SearchHit> results = vectorSearchService.searchMultiCollection(
                    List.of("kb_public", "kb_private"), "多库检索", 5, 3);

            // 按 score 升序排列 → B(0.1) 在 A(0.5) 前面
            assertEquals(2, results.size());
            assertEquals("Result B", results.get(0).getContent());
            assertEquals("Result A", results.get(1).getContent());
        }

        @Test
        @DisplayName("空 Collection 列表 → 返回空列表")
        void searchMulti_emptyList_shouldReturnEmpty() {
            List<SearchTestResponse.SearchHit> results = vectorSearchService.searchMultiCollection(
                    List.of(), "query", 5, 3);

            assertTrue(results.isEmpty());
            verify(embeddingService, never()).embed(anyString());
        }

        @Test
        @DisplayName("null Collection 列表 → 返回空列表")
        void searchMulti_nullList_shouldReturnEmpty() {
            List<SearchTestResponse.SearchHit> results = vectorSearchService.searchMultiCollection(
                    null, "query", 5, 3);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("某个 Collection 检索异常 → 跳过该 Collection 继续")
        void searchMulti_oneFails_shouldSkipAndContinue() {
            when(embeddingService.embed("test")).thenReturn(SAMPLE_VECTOR);
            when(milvusClient.search(any(SearchReq.class)))
                    .thenThrow(new RuntimeException("Collection not found"))
                    .thenReturn(buildSearchResp(List.of(
                            hit("doc-ok", 0, "OK Result", 0.2f)
                    )));

            List<SearchTestResponse.SearchHit> results = vectorSearchService.searchMultiCollection(
                    List.of("kb_broken", "kb_good"), "test", 5, 3);

            assertEquals(1, results.size());
            assertEquals("OK Result", results.get(0).getContent());
        }

        @Test
        @DisplayName("结果数超过 finalTopK → 截断到 finalTopK")
        void searchMulti_exceedFinalTopK_shouldTruncate() {
            when(embeddingService.embed("test")).thenReturn(SAMPLE_VECTOR);
            when(milvusClient.search(any(SearchReq.class)))
                    .thenReturn(buildSearchResp(List.of(
                            hit("d1", 0, "R1", 0.1f),
                            hit("d2", 0, "R2", 0.3f),
                            hit("d3", 0, "R3", 0.5f)
                    )));

            List<SearchTestResponse.SearchHit> results = vectorSearchService.searchMultiCollection(
                    List.of("kb_001"), "test", 10, 2);

            assertEquals(2, results.size());
        }
    }

    // ==================== helpers ====================

    private SearchResp buildSearchResp(List<SearchTestResponse.SearchHit> hits) {
        List<List<SearchResp.SearchResult>> searchResults = new ArrayList<>();

        List<SearchResp.SearchResult> resultList = new ArrayList<>();
        for (SearchTestResponse.SearchHit hit : hits) {
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("doc_id", hit.getDocId());
            entity.put("chunk_index", (long) (hit.getChunkIndex() != null ? hit.getChunkIndex() : 0));
            entity.put("content", hit.getContent());

            SearchResp.SearchResult sr = SearchResp.SearchResult.builder()
                    .entity(entity)
                    .distance(hit.getScore())
                    .build();
            resultList.add(sr);
        }
        searchResults.add(resultList);

        return SearchResp.builder()
                .searchResults(searchResults)
                .build();
    }

    private SearchTestResponse.SearchHit hit(String docId, int chunkIndex, String content, float score) {
        SearchTestResponse.SearchHit h = new SearchTestResponse.SearchHit();
        h.setDocId(docId);
        h.setChunkIndex(chunkIndex);
        h.setContent(content);
        h.setScore(score);
        return h;
    }
}
