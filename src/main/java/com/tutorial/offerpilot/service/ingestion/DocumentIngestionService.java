/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service.ingestion;

import com.alibaba.fastjson.JSONObject;
import com.tutorial.offerpilot.entity.KbChunk;
import com.tutorial.offerpilot.entity.KbDocument;
import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import com.tutorial.offerpilot.repository.ChunkRepository;
import com.tutorial.offerpilot.repository.DocumentRepository;
import com.tutorial.offerpilot.repository.KnowledgeBaseRepository;
import com.tutorial.offerpilot.service.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * 文档异步入库管道：PARSING → CHUNKING → EMBEDDING → INDEXING → ACTIVE。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository docRepo;
    private final ChunkRepository chunkRepo;
    private final KnowledgeBaseRepository kbRepo;
    private final MilvusClientV2 milvusClient;
    private final EmbeddingService embeddingService;
    private final DocumentParser parser;
    private final DocumentChunker chunker;

    /**
     * 异步执行文档入库管道：PARSING → CHUNKING → EMBEDDING → INDEXING → ACTIVE。
     */
    @Async("ingestionExecutor")
    public void ingestDocument(String docId) {
        log.info("Document ingestion started: docId={}", docId);

        KbDocument doc = docRepo.findByDocId(docId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));

        try {
            // Phase 1: PARSING
            updateStatus(doc, "PARSING", 10);
            String text = parser.parse(doc.getFilePath(), doc.getFileType());
            log.info("Parsed document: docId={}, textLength={}", docId, text.length());

            // Phase 2: CHUNKING
            updateStatus(doc, "CHUNKING", 30);
            List<String> chunks = chunker.chunk(text, doc.getChunkStrategy());
            log.info("Chunked document: docId={}, chunkCount={}", docId, chunks.size());

            if (chunks.isEmpty()) {
                updateStatus(doc, "FAILED", 0);
                doc.setErrorMessage("文档分块结果为空");
                docRepo.save(doc);
                return;
            }

            // Phase 3: EMBEDDING
            updateStatus(doc, "EMBEDDING", 50);
            String collectionName = getCollectionName(doc.getKbId());

            // 批量 Embedding（减少 API 调用次数，节省成本）
            List<float[]> vectors = embeddingService.embedBatch(chunks);

            List<KbChunk> chunkEntities = new ArrayList<>();
            List<JSONObject> milvusRows = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] vector = vectors.get(i);

                // 构建分块实体
                KbChunk chunk = new KbChunk();
                chunk.setDocId(docId);
                chunk.setKbId(doc.getKbId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunkText);
                chunk.setContentHash(hashContent(chunkText));
                chunk.setTokenCount(estimateTokenCount(chunkText));
                chunk.setMilvusOffset((long) i);
                chunkEntities.add(chunk);

                // 构建 Milvus 插入行
                JSONObject row = new JSONObject();
                row.put("doc_id", docId);
                row.put("chunk_index", i);
                row.put("content", chunkText);
                row.put("vector", toFloatList(vector));
                milvusRows.add(row);
            }

            log.info("Embedding complete: docId={}, vectorCount={}", docId, chunks.size());

            // Phase 4: INDEXING — 写入 Milvus
            updateStatus(doc, "INDEXING", 80);
            InsertResp insertResp = milvusClient.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(milvusRows)
                    .build());
            log.info("Inserted vectors into Milvus: docId={}, count={}", docId, insertResp.getInsertCnt());

            // 保存分块到数据库
            chunkRepo.saveAll(chunkEntities);

            // 更新文档统计信息
            doc.setChunkCount(chunks.size());
            doc.setIndexedAt(Instant.now());

            // 更新知识库统计信息
            KbKnowledgeBase kb = kbRepo.findByKbId(doc.getKbId()).orElse(null);
            if (kb != null) {
                kb.setChunkCount(kb.getChunkCount() + chunks.size());
                kb.setDocumentCount(kb.getDocumentCount() + 1);
                kbRepo.save(kb);
            }

            // Phase 5: ACTIVE
            updateStatus(doc, "ACTIVE", 100);
            log.info("Document ingestion completed: docId={}, chunks={}", docId, chunks.size());

        } catch (IOException e) {
            log.error("Document ingestion failed: docId={}", docId, e);
            updateStatus(doc, "FAILED", 0);
            doc.setErrorMessage("文件解析失败: " + e.getMessage());
            docRepo.save(doc);
        } catch (Exception e) {
            log.error("Document ingestion failed: docId={}", docId, e);
            updateStatus(doc, "FAILED", 0);
            doc.setErrorMessage("入库失败: " + e.getMessage());
            docRepo.save(doc);
        }
    }

    private void updateStatus(KbDocument doc, String status, int progress) {
        doc.setStatus(status);
        doc.setProgress(progress);
        docRepo.save(doc);
        log.info("Document status: docId={}, status={}, progress={}%", doc.getDocId(), status, progress);
    }

    private String getCollectionName(String kbId) {
        return kbRepo.findByKbId(kbId)
                .map(KbKnowledgeBase::getMilvusCollection)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + kbId));
    }

    private String hashContent(String content) {
        return Integer.toHexString(content.hashCode());
    }

    private int estimateTokenCount(String text) {
        // 粗略估算：中文约每字符 1 token，英文约每 4 字符 1 token
        return text.length();
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }
}
