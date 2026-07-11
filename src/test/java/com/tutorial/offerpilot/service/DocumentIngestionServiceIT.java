/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.entity.KbChunk;
import com.tutorial.offerpilot.entity.KbDocument;
import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import com.tutorial.offerpilot.repository.ChunkRepository;
import com.tutorial.offerpilot.repository.DocumentRepository;
import com.tutorial.offerpilot.repository.KnowledgeBaseRepository;
import com.tutorial.offerpilot.service.ingestion.DocumentIngestionService;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@DisplayName("DocumentIngestionService 集成测试")
class DocumentIngestionServiceIT extends AbstractServiceIT {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private DocumentRepository docRepo;

    @Autowired
    private ChunkRepository chunkRepo;

    @Autowired
    private KnowledgeBaseRepository kbRepo;

    @MockBean
    private EmbeddingService embeddingService;

    private KbKnowledgeBase testKb;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        reset(milvusClient, embeddingService);

        // 创建测试知识库
        testKb = new KbKnowledgeBase();
        testKb.setKbId("kb-test-ingest-001");
        testKb.setName("测试知识库");
        testKb.setDescription("用于异步入库测试");
        testKb.setMilvusCollection("kb_kb-test-ingest-001");
        testKb.setCategory("测试");
        testKb.setVisibility("PUBLIC");
        testKb.setStatus("ACTIVE");
        testKb.setDocumentCount(0);
        testKb.setChunkCount(0);
        testKb.setCreateBy("test");
        kbRepo.saveAndFlush(testKb);

        // 创建测试文件（TXT 格式，最简单）
        testFile = Path.of("./target/test-uploads/test-ingestion");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, """
                ## 第一章 概述
                这是测试文档的第一章内容，用于验证异步入库管道的功能。
                包含足够长的文本以确保分块策略能够正常工作。
                
                ## 第二章 详细说明
                第二章包含更多测试内容，用于验证文档分块的正确性。
                这里应该有足够的内容来触发分块逻辑。
                
                ## 第三章 总结
                最后一章包含总结性的测试内容。
                """);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    // ==================== ingestDocument success ====================

    @Nested
    @DisplayName("ingestDocument 成功路径")
    class IngestSuccessTests {

        @Test
        @DisplayName("TXT文档 → 完整管道 PARSING→CHUNKING→EMBEDDING→INDEXING→ACTIVE")
        void ingestDocument_txt_shouldCompletePipeline() throws Exception {
            // Arrange: 创建文档记录
            KbDocument doc = new KbDocument();
            doc.setDocId("doc-test-ingest-001");
            doc.setKbId(testKb.getKbId());
            doc.setFileName("test-doc.txt");
            doc.setFilePath(testFile.toString());
            doc.setFileType("txt");
            doc.setFileSize(Files.size(testFile));
            doc.setChunkStrategy("AUTO");
            doc.setStatus("UPLOADED");
            doc.setCreateBy("test");
            docRepo.saveAndFlush(doc);

            // Mock EmbeddingService 返回固定向量
            float[] vector = new float[1024];
            vector[0] = 0.5f;
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(vector));

            // Mock Milvus insert 成功
            InsertResp insertResp = InsertResp.builder().InsertCnt(1L).build();
            when(milvusClient.insert(any(InsertReq.class))).thenReturn(insertResp);

            // Act: 触发异步入库
            ingestionService.doIngestDocument("doc-test-ingest-001");

            // 等待异步管道完成（轮询最多 10 秒）
            KbDocument updated = waitForStatus("doc-test-ingest-001", "ACTIVE", 10_000);

            // Assert: 状态变更
            assertNotNull(updated, "文档应在 10 秒内变为 ACTIVE 状态");
            assertEquals("ACTIVE", updated.getStatus());
            assertEquals(100, updated.getProgress());
            assertNotNull(updated.getIndexedAt());

            // Assert: 分块已保存
            List<KbChunk> chunks = chunkRepo.findByDocIdOrderByChunkIndex("doc-test-ingest-001");
            assertFalse(chunks.isEmpty(), "应有至少 1 个分块");
            assertEquals(updated.getChunkCount(), chunks.size());

            // Assert: Milvus insert 被调用
            verify(milvusClient).insert(argThat(req ->
                    req.getCollectionName().equals("kb_kb-test-ingest-001")));

            // Assert: 知识库统计更新
            KbKnowledgeBase kb = kbRepo.findByKbId(testKb.getKbId()).orElseThrow();
            assertEquals(1, kb.getDocumentCount());
            assertTrue(kb.getChunkCount() > 0);
        }
    }

    // ==================== ingestDocument failure ====================

    @Nested
    @DisplayName("ingestDocument 失败路径")
    class IngestFailureTests {

        @Test
        @DisplayName("文档不存在 → 异步执行不抛同步异常（异常在线程池内处理）")
        void ingestDocument_notFound_shouldNotThrowSynchronously() {
            // @Async 方法的异常在线程池内消化，不会传播到调用方
            assertDoesNotThrow(() -> ingestionService.doIngestDocument("non-existent-doc"));
        }

        @Test
        @DisplayName("文件不存在 → 状态变为 FAILED")
        void ingestDocument_fileNotFound_shouldMarkFailed() throws Exception {
            KbDocument doc = new KbDocument();
            doc.setDocId("doc-test-fail-001");
            doc.setKbId(testKb.getKbId());
            doc.setFileName("missing.txt");
            doc.setFilePath("./target/test-uploads/non-existent-file.txt");
            doc.setFileType("txt");
            doc.setFileSize(0L);
            doc.setChunkStrategy("AUTO");
            doc.setStatus("UPLOADED");
            doc.setCreateBy("test");
            docRepo.saveAndFlush(doc);

            ingestionService.doIngestDocument("doc-test-fail-001");

            KbDocument updated = waitForStatus("doc-test-fail-001", "FAILED", 10_000);

            assertNotNull(updated);
            assertEquals("FAILED", updated.getStatus());
            assertNotNull(updated.getErrorMessage());
        }
    }

    // ==================== 不支持的文件类型 ====================

    @Nested
    @DisplayName("ingestDocument 不支持的文件类型")
    class IngestUnsupportedTypeTests {

        @Test
        @DisplayName("不支持的文件类型 → 状态变为 FAILED")
        void ingestDocument_unsupportedType_shouldMarkFailed() throws Exception {
            // 创建 .exe 文件
            Path exeFile = Path.of("./target/test-uploads/test-unsupported.exe");
            Files.createDirectories(exeFile.getParent());
            Files.writeString(exeFile, "fake exe content");

            try {
                KbDocument doc = new KbDocument();
                doc.setDocId("doc-test-unsupported-001");
                doc.setKbId(testKb.getKbId());
                doc.setFileName("bad.exe");
                doc.setFilePath(exeFile.toString());
                doc.setFileType("exe");
                doc.setFileSize(Files.size(exeFile));
                doc.setChunkStrategy("AUTO");
                doc.setStatus("UPLOADED");
                doc.setCreateBy("test");
                docRepo.saveAndFlush(doc);

                ingestionService.doIngestDocument("doc-test-unsupported-001");

                KbDocument updated = waitForStatus("doc-test-unsupported-001", "FAILED", 10_000);

                assertNotNull(updated);
                assertEquals("FAILED", updated.getStatus());
                assertNotNull(updated.getErrorMessage());
            } finally {
                Files.deleteIfExists(exeFile);
            }
        }
    }

    // ==================== Milvus 写入失败 ====================

    @Nested
    @DisplayName("ingestDocument Milvus 写入失败")
    class MilvusFailureTests {

        @Test
        @DisplayName("Milvus insert 异常 → 状态变为 FAILED")
        void ingestDocument_milvusFails_shouldMarkFailed() throws Exception {
            KbDocument doc = new KbDocument();
            doc.setDocId("doc-test-milvus-fail-001");
            doc.setKbId(testKb.getKbId());
            doc.setFileName("test-doc-mf.txt");
            doc.setFilePath(testFile.toString());
            doc.setFileType("txt");
            doc.setFileSize(Files.size(testFile));
            doc.setChunkStrategy("AUTO");
            doc.setStatus("UPLOADED");
            doc.setCreateBy("test");
            docRepo.saveAndFlush(doc);

            float[] vector = new float[1024];
            vector[0] = 0.5f;
            when(embeddingService.embedBatch(anyList())).thenReturn(List.of(vector));
            when(milvusClient.insert(any(InsertReq.class)))
                    .thenThrow(new RuntimeException("Milvus connection refused"));

            ingestionService.doIngestDocument("doc-test-milvus-fail-001");

            KbDocument updated = waitForStatus("doc-test-milvus-fail-001", "FAILED", 15_000);

            assertNotNull(updated);
            assertEquals("FAILED", updated.getStatus());
            assertNotNull(updated.getErrorMessage());
            assertTrue(updated.getErrorMessage().contains("入库失败"));
        }
    }

    // ==================== helpers ====================

    /**
     * 轮询等待文档状态变为 targetStatus（通过 JDBC 直接查询，绕过 JPA 缓存）。
     */
    private KbDocument waitForStatus(String docId, String targetStatus, long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            // 通过 JDBC 直接查，避免 JPA 一级缓存看不到异步线程的更新
            String sql = "SELECT status FROM kb_document WHERE doc_id = ? AND create_by = 'test'";
            var rows = jdbcTemplate.queryForList(sql, docId);
            if (!rows.isEmpty() && targetStatus.equals(String.valueOf(rows.get(0).get("status")))) {
                docRepo.flush();
                return docRepo.findByDocId(docId).orElse(null);
            }
            Thread.sleep(200);
        }
        // 最终尝试通过 JPA 获取
        docRepo.flush();
        return docRepo.findByDocId(docId).orElse(null);
    }
}
