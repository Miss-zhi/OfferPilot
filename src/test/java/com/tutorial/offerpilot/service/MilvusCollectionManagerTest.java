/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MilvusCollectionManager 单元测试")
class MilvusCollectionManagerTest {

    @Mock
    private MilvusClientV2 milvusClient;

    private MilvusCollectionManager collectionManager;

    private static final String COLLECTION_NAME = "kb_test_collection";

    @BeforeEach
    void setUp() {
        collectionManager = new MilvusCollectionManager(milvusClient);
    }

    // ==================== createCollection ====================

    @Nested
    @DisplayName("createCollection")
    class CreateCollectionTests {

        @Test
        @DisplayName("Collection 不存在 → 创建成功，返回 true")
        void createCollection_notExists_shouldCreate() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            boolean result = collectionManager.createCollection(COLLECTION_NAME);

            assertTrue(result);
            verify(milvusClient).createCollection(any(CreateCollectionReq.class));
            verify(milvusClient).loadCollection(any(LoadCollectionReq.class));
        }

        @Test
        @DisplayName("Collection 已存在 → 跳过创建，返回 false")
        void createCollection_alreadyExists_shouldSkip() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(true);

            boolean result = collectionManager.createCollection(COLLECTION_NAME);

            assertFalse(result);
            verify(milvusClient, never()).createCollection(any(CreateCollectionReq.class));
            verify(milvusClient, never()).loadCollection(any(LoadCollectionReq.class));
        }

        @Test
        @DisplayName("创建时 Schema 包含正确的字段定义")
        void createCollection_shouldHaveCorrectSchema() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            collectionManager.createCollection(COLLECTION_NAME);

            ArgumentCaptor<CreateCollectionReq> captor = ArgumentCaptor.forClass(CreateCollectionReq.class);
            verify(milvusClient).createCollection(captor.capture());

            CreateCollectionReq req = captor.getValue();
            assertEquals(COLLECTION_NAME, req.getCollectionName());
            assertNotNull(req.getCollectionSchema());
            assertEquals(5, req.getCollectionSchema().getFieldSchemaList().size());

            // 验证主键字段
            var pkField = req.getCollectionSchema().getFieldSchemaList().get(0);
            assertEquals("id", pkField.getName());
            assertTrue(pkField.getIsPrimaryKey());
            assertTrue(pkField.getAutoID());

            // 验证向量字段
            var vectorField = req.getCollectionSchema().getFieldSchemaList().get(4);
            assertEquals("vector", vectorField.getName());
            assertEquals(1024, vectorField.getDimension());
        }
    }

    // ==================== hasCollection ====================

    @Nested
    @DisplayName("hasCollection")
    class HasCollectionTests {

        @Test
        @DisplayName("Collection 存在 → 返回 true")
        void hasCollection_exists_shouldReturnTrue() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(true);

            boolean result = collectionManager.hasCollection(COLLECTION_NAME);

            assertTrue(result);
        }

        @Test
        @DisplayName("Collection 不存在 → 返回 false")
        void hasCollection_notExists_shouldReturnFalse() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            boolean result = collectionManager.hasCollection(COLLECTION_NAME);

            assertFalse(result);
        }

        @Test
        @DisplayName("Milvus 异常 → 返回 false（不抛异常）")
        void hasCollection_error_shouldReturnFalse() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class)))
                    .thenThrow(new RuntimeException("连接失败"));

            boolean result = collectionManager.hasCollection(COLLECTION_NAME);

            assertFalse(result);
        }
    }

    // ==================== dropCollection ====================

    @Nested
    @DisplayName("dropCollection")
    class DropCollectionTests {

        @Test
        @DisplayName("Collection 存在 → 删除成功，返回 true")
        void dropCollection_exists_shouldDrop() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(true);

            boolean result = collectionManager.dropCollection(COLLECTION_NAME);

            assertTrue(result);
            verify(milvusClient).dropCollection(any(DropCollectionReq.class));
        }

        @Test
        @DisplayName("Collection 不存在 → 跳过删除，返回 false")
        void dropCollection_notExists_shouldSkip() {
            when(milvusClient.hasCollection(any(HasCollectionReq.class))).thenReturn(false);

            boolean result = collectionManager.dropCollection(COLLECTION_NAME);

            assertFalse(result);
            verify(milvusClient, never()).dropCollection(any(DropCollectionReq.class));
        }
    }

    // ==================== listCollections ====================

    @Nested
    @DisplayName("listCollections")
    class ListCollectionsTests {

        @Test
        @DisplayName("正常 → 返回 Collection 名列表")
        void listCollections_shouldReturnNames() {
            ListCollectionsResp resp = ListCollectionsResp.builder()
                    .collectionNames(List.of("coll_a", "coll_b", "coll_c"))
                    .build();
            when(milvusClient.listCollections()).thenReturn(resp);

            ListCollectionsResp result = collectionManager.listCollections();

            assertEquals(3, result.getCollectionNames().size());
            assertTrue(result.getCollectionNames().contains("coll_a"));
        }

        @Test
        @DisplayName("无 Collection → 返回空列表")
        void listCollections_empty_shouldReturnEmptyList() {
            ListCollectionsResp resp = ListCollectionsResp.builder()
                    .collectionNames(List.of())
                    .build();
            when(milvusClient.listCollections()).thenReturn(resp);

            ListCollectionsResp result = collectionManager.listCollections();

            assertTrue(result.getCollectionNames().isEmpty());
        }
    }
}
