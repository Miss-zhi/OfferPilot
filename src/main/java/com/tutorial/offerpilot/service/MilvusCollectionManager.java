/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Milvus Collection 生命周期管理。
 * 负责知识库对应的向量集合的创建、检查和删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusCollectionManager {

    private final MilvusClientV2 milvusClient;

    private static final int DEFAULT_VECTOR_DIM = 1024;

    /**
     * 为知识库创建对应的 Milvus Collection。
     * 包含 id（主键）、doc_id、chunk_index、content、vector 字段。
     */
    public boolean createCollection(String collectionName) {
        if (hasCollection(collectionName)) {
            log.info("Milvus collection already exists: {}", collectionName);
            return false;
        }

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(List.of(
                        CreateCollectionReq.FieldSchema.builder()
                                .name("id")
                                .dataType(DataType.Int64)
                                .isPrimaryKey(Boolean.TRUE)
                                .autoID(Boolean.TRUE)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("doc_id")
                                .dataType(DataType.VarChar)
                                .maxLength(64)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("chunk_index")
                                .dataType(DataType.Int32)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("content")
                                .dataType(DataType.VarChar)
                                .maxLength(65535)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("vector")
                                .dataType(DataType.FloatVector)
                                .dimension(DEFAULT_VECTOR_DIM)
                                .build()
                ))
                .build();

        CreateCollectionReq req = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .build();

        milvusClient.createCollection(req);
        log.info("Milvus collection created: {}, dim={}", collectionName, DEFAULT_VECTOR_DIM);
        return true;
    }

    /**
     * 检查 Collection 是否存在。
     */
    public boolean hasCollection(String collectionName) {
        try {
            return milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to check Milvus collection {}: {}", collectionName, e.getMessage());
            return false;
        }
    }

    /**
     * 删除 Collection。
     */
    public boolean dropCollection(String collectionName) {
        if (!hasCollection(collectionName)) {
            log.info("Milvus collection not found, skip drop: {}", collectionName);
            return false;
        }
        milvusClient.dropCollection(DropCollectionReq.builder()
                .collectionName(collectionName)
                .build());
        log.info("Milvus collection dropped: {}", collectionName);
        return true;
    }

    /**
     * 列出所有 Collection。
     */
    public ListCollectionsResp listCollections() {
        return milvusClient.listCollections();
    }
}
