/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorSearchService 单元测试")
class VectorSearchServiceTest {

    @Mock
    private MilvusClientV2 milvusClient;

    @Mock
    private EmbeddingService embeddingService;

    private VectorSearchService vectorSearchService;

    private static final String COLLECTION_NAME = "kb_test";
    private static final float[] QUERY_VECTOR = {0.1f, 0.2f, 0.3f};

    @BeforeEach
    void setUp() {
        vectorSearchService = new VectorSearchService(milvusClient, embeddingService);
    }

    /** 构造一条 Milvus 检索结果 */
    private SearchResp.SearchResult buildSearchResult(String docId, int chunkIndex, String content, float distance) {
        return SearchResp.SearchResult.builder()
                .entity(Map.of("doc_id", docId, "chunk_index", chunkIndex, "content", content))
                .distance(distance)
                .build();
    }

    // ==================== search ====================

    @Nested
    @DisplayName("search")
    class SearchTests {

        @Test
        @DisplayName("正常检索 → 返回按距离排序的结果")
        void search_shouldReturnSortedHits() {
            when(embeddingService.embed("Java面试")).thenReturn(QUERY_VECTOR);

            SearchResp.SearchResult r1 = buildSearchResult("doc-1", 0, "Java基础", 0.3f);
            SearchResp.SearchResult r2 = buildSearchResult("doc-2", 1, "JVM调优", 0.5f);
            SearchResp.SearchResult r3 = buildSearchResult("doc-3", 2, "Spring框架", 0.1f);
            SearchResp searchResp = SearchResp.builder()
                    .searchResults(List.of(List.of(r1, r2, r3)))
                    .build();
            when(milvusClient.search(any())).thenReturn(searchResp);

            List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(COLLECTION_NAME, "Java面试", 3);

            assertEquals(3, hits.size());
            // Milvus 返回顺序即为结果顺序；distances: 0.3, 0.5, 0.1
            assertEquals("doc-1", hits.get(0).getDocId());
            assertEquals("doc-2", hits.get(1).getDocId());
            assertEquals("doc-3", hits.get(2).getDocId());
        }

        @Test
        @DisplayName("无结果 → 返回空列表")
        void search_emptyResult_shouldReturnEmptyList() {
            when(embeddingService.embed("不存在的内容")).thenReturn(QUERY_VECTOR);

            SearchResp searchResp = SearchResp.builder()
                    .searchResults(List.of(Collections.emptyList()))
                    .build();
            when(milvusClient.search(any())).thenReturn(searchResp);

            List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(COLLECTION_NAME, "不存在的内容", 5);

            assertTrue(hits.isEmpty());
        }

        @Test
        @DisplayName("searchResults 为 null → 返回空列表")
        void search_nullSearchResults_shouldReturnEmptyList() {
            when(embeddingService.embed(anyString())).thenReturn(QUERY_VECTOR);

            SearchResp searchResp = SearchResp.builder().searchResults(null).build();
            when(milvusClient.search(any())).thenReturn(searchResp);

            List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(COLLECTION_NAME, "test", 5);

            assertTrue(hits.isEmpty());
        }

        @Test
        @DisplayName("SearchResult entity 为 null → 跳过该条")
        void search_nullEntity_shouldSkip() {
            when(embeddingService.embed(anyString())).thenReturn(QUERY_VECTOR);

            SearchResp.SearchResult result = SearchResp.SearchResult.builder()
                    .entity(null)
                    .distance(0.5f)
                    .build();
            SearchResp searchResp = SearchResp.builder()
                    .searchResults(List.of(List.of(result)))
                    .build();
            when(milvusClient.search(any())).thenReturn(searchResp);

            List<SearchTestResponse.SearchHit> hits = vectorSearchService.search(COLLECTION_NAME, "test", 5);

            assertTrue(hits.isEmpty());
        }

        @Test
        @DisplayName("Milvus 搜索异常 → 向上抛出")
        void search_milvusError_shouldPropagate() {
            when(embeddingService.embed(anyString())).thenReturn(QUERY_VECTOR);
            when(milvusClient.search(any())).thenThrow(new RuntimeException("连接超时"));

            assertThrows(RuntimeException.class,
                    () -> vectorSearchService.search(COLLECTION_NAME, "test", 5));
        }
    }

    // ==================== searchMultiCollection ====================

    @Nested
    @DisplayName("searchMultiCollection")
    class SearchMultiCollectionTests {

        @Test
        @DisplayName("多 Collection 检索 → 合并并按距离升序取 Top-K")
        void searchMultiCollection_shouldMergeAndSort() {
            when(embeddingService.embed("多库检索")).thenReturn(QUERY_VECTOR);

            // Collection A: 返回 2 条
            SearchResp.SearchResult a1 = buildSearchResult("a-1", 0, "A内容1", 0.8f);
            SearchResp.SearchResult a2 = buildSearchResult("a-2", 1, "A内容2", 0.2f);
            SearchResp respA = SearchResp.builder()
                    .searchResults(List.of(List.of(a1, a2)))
                    .build();

            // Collection B: 返回 1 条
            SearchResp.SearchResult b1 = buildSearchResult("b-1", 0, "B内容1", 0.5f);
            SearchResp respB = SearchResp.builder()
                    .searchResults(List.of(List.of(b1)))
                    .build();

            when(milvusClient.search(any()))
                    .thenReturn(respA)
                    .thenReturn(respB);

            List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                    List.of("coll_a", "coll_b"), "多库检索", 5, 2);

            // 合并后按距离升序：a2(0.2) < b1(0.5) < a1(0.8), finalTopK=2
            assertEquals(2, hits.size());
            assertEquals("a-2", hits.get(0).getDocId());
            assertEquals(0.2f, hits.get(0).getScore(), 0.001f);
            assertEquals("b-1", hits.get(1).getDocId());
            assertEquals(0.5f, hits.get(1).getScore(), 0.001f);
        }

        @Test
        @DisplayName("空 Collection 列表 → 返回空")
        void searchMultiCollection_emptyList_shouldReturnEmpty() {
            List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                    Collections.emptyList(), "test", 5, 5);

            assertTrue(hits.isEmpty());
            verifyNoInteractions(embeddingService);
        }

        @Test
        @DisplayName("null Collection 列表 → 返回空")
        void searchMultiCollection_nullList_shouldReturnEmpty() {
            List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                    null, "test", 5, 5);

            assertTrue(hits.isEmpty());
        }

        @Test
        @DisplayName("某个 Collection 搜索失败 → 跳过异常 Collection，返回其余")
        void searchMultiCollection_oneFails_shouldSkipAndContinue() {
            when(embeddingService.embed("test")).thenReturn(QUERY_VECTOR);

            SearchResp.SearchResult r1 = buildSearchResult("doc-1", 0, "内容", 0.3f);
            SearchResp resp = SearchResp.builder()
                    .searchResults(List.of(List.of(r1)))
                    .build();

            when(milvusClient.search(any()))
                    .thenThrow(new RuntimeException("Collection not found"))
                    .thenReturn(resp);

            List<SearchTestResponse.SearchHit> hits = vectorSearchService.searchMultiCollection(
                    List.of("bad_coll", "good_coll"), "test", 5, 5);

            assertEquals(1, hits.size());
            assertEquals("doc-1", hits.get(0).getDocId());
        }
    }
}
