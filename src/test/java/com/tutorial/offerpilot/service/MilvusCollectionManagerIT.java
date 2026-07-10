/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@DisplayName("MilvusCollectionManager 集成测试")
class MilvusCollectionManagerIT extends AbstractServiceIT {

    @Autowired
    private MilvusCollectionManager collectionManager;

    @BeforeEach
    void resetMocks() {
        reset(milvusClient);
    }

    // ==================== hasCollection ====================

    @Nested
    @DisplayName("hasCollection")
    class HasCollectionTests {

        @Test
        @DisplayName("Collection 存在 → 返回 true")
        void collectionExists_shouldReturnTrue() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(true);

            assertTrue(collectionManager.hasCollection("test_collection"));
        }

        @Test
        @DisplayName("Collection 不存在 → 返回 false")
        void collectionNotExists_shouldReturnFalse() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            assertFalse(collectionManager.hasCollection("nonexistent"));
        }

        @Test
        @DisplayName("Milvus 调用异常 → 返回 false")
        void milvusException_shouldReturnFalse() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertFalse(collectionManager.hasCollection("error_collection"));
        }
    }

    // ==================== createCollection ====================

    @Nested
    @DisplayName("createCollection")
    class CreateCollectionTests {

        @Test
        @DisplayName("Collection 不存在 → 创建成功返回 true")
        void createNewCollection_shouldReturnTrue() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            boolean result = collectionManager.createCollection("kb_test_001");

            assertTrue(result);
            verify(milvusClient).createCollection(any(CreateCollectionReq.class));
        }

        @Test
        @DisplayName("Collection 已存在 → 不创建，返回 false")
        void createExistingCollection_shouldReturnFalse() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(true);

            boolean result = collectionManager.createCollection("kb_existing");

            assertFalse(result);
            verify(milvusClient, never()).createCollection(any());
        }

        @Test
        @DisplayName("创建的 Collection 包含正确的 Schema 字段")
        void createdCollection_shouldHaveCorrectSchema() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            collectionManager.createCollection("kb_schema_test");

            verify(milvusClient).createCollection(argThat(req -> {
                var schema = req.getCollectionSchema();
                var fields = schema.getFieldSchemaList();
                // 验证 5 个字段: id, doc_id, chunk_index, content, vector
                return fields.size() == 5
                        && fields.stream().anyMatch(f -> "id".equals(f.getName()) && f.getIsPrimaryKey())
                        && fields.stream().anyMatch(f -> "doc_id".equals(f.getName()))
                        && fields.stream().anyMatch(f -> "chunk_index".equals(f.getName()))
                        && fields.stream().anyMatch(f -> "content".equals(f.getName()))
                        && fields.stream().anyMatch(f -> "vector".equals(f.getName()));
            }));
        }
    }

    // ==================== dropCollection ====================

    @Nested
    @DisplayName("dropCollection")
    class DropCollectionTests {

        @Test
        @DisplayName("Collection 存在 → 删除成功返回 true")
        void dropExistingCollection_shouldReturnTrue() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(true);

            boolean result = collectionManager.dropCollection("kb_to_delete");

            assertTrue(result);
            verify(milvusClient).dropCollection(any(DropCollectionReq.class));
        }

        @Test
        @DisplayName("Collection 不存在 → 不删除，返回 false")
        void dropNonexistentCollection_shouldReturnFalse() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            boolean result = collectionManager.dropCollection("kb_nonexistent");

            assertFalse(result);
            verify(milvusClient, never()).dropCollection(any());
        }
    }

    // ==================== listCollections ====================

    @Nested
    @DisplayName("listCollections")
    class ListCollectionsTests {

        @Test
        @DisplayName("列出所有 Collection → 返回 ListCollectionsResp")
        void listCollections_shouldReturnResp() {
            ListCollectionsResp expectedResp = ListCollectionsResp.builder()
                    .collectionNames(List.of("kb_001", "kb_002"))
                    .build();
            when(milvusClient.listCollections()).thenReturn(expectedResp);

            ListCollectionsResp result = collectionManager.listCollections();

            assertNotNull(result);
            assertEquals(2, result.getCollectionNames().size());
            assertTrue(result.getCollectionNames().contains("kb_001"));
        }
    }
}
