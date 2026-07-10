/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.converter;

import com.tutorial.offerpilot.dto.kb.DocDetailResponse;
import com.tutorial.offerpilot.dto.kb.DocResponse;
import com.tutorial.offerpilot.entity.KbChunk;
import com.tutorial.offerpilot.entity.KbDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KbConverter 转换器测试")
class KbConverterTest {

    private final KbConverter converter = new KbConverter();

    private KbDocument buildDoc(String docId, String kbId) {
        KbDocument doc = new KbDocument();
        doc.setDocId(docId);
        doc.setKbId(kbId);
        doc.setFileName("test.pdf");
        doc.setFileType("pdf");
        doc.setFileSize(1024L);
        doc.setChunkCount(10);
        doc.setChunkStrategy("AUTO");
        doc.setStatus("ACTIVE");
        doc.setProgress(100);
        doc.setTags("java,面试");
        doc.setUploadedAt(Instant.parse("2026-07-10T00:00:00Z"));
        doc.setIndexedAt(Instant.parse("2026-07-10T01:00:00Z"));
        return doc;
    }

    private KbChunk buildChunk(int index, String content) {
        KbChunk chunk = new KbChunk();
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setTokenCount(content.length());
        return chunk;
    }

    // ==================== toDocResponse ====================

    @Nested
    @DisplayName("toDocResponse")
    class ToDocResponseTests {

        @Test
        @DisplayName("正常转换 → 所有字段一一映射")
        void toDocResponse_shouldMapAllFields() {
            KbDocument doc = buildDoc("doc-001", "kb-001");

            DocResponse resp = converter.toDocResponse(doc);

            assertEquals("doc-001", resp.getDocId());
            assertEquals("kb-001", resp.getKbId());
            assertEquals("test.pdf", resp.getFileName());
            assertEquals("pdf", resp.getFileType());
            assertEquals(1024L, resp.getFileSize());
            assertEquals(10, resp.getChunkCount());
            assertEquals("AUTO", resp.getChunkStrategy());
            assertEquals("ACTIVE", resp.getStatus());
            assertEquals(100, resp.getProgress());
            assertEquals("java,面试", resp.getTags());
            assertEquals(Instant.parse("2026-07-10T00:00:00Z"), resp.getUploadedAt());
            assertEquals(Instant.parse("2026-07-10T01:00:00Z"), resp.getIndexedAt());
        }

        @Test
        @DisplayName("nullable 字段 → 不会 NPE")
        void toDocResponse_nullableFields_shouldNotNpe() {
            KbDocument doc = new KbDocument();
            doc.setDocId("doc-min");
            doc.setKbId("kb-min");

            DocResponse resp = converter.toDocResponse(doc);

            assertEquals("doc-min", resp.getDocId());
            assertEquals("kb-min", resp.getKbId());
            assertNull(resp.getFileName());
            assertNull(resp.getTags());
            assertNull(resp.getUploadedAt());
        }
    }

    // ==================== toDocDetailResponse ====================

    @Nested
    @DisplayName("toDocDetailResponse")
    class ToDocDetailResponseTests {

        @Test
        @DisplayName("正常转换 → 包含文档字段 + 分块预览列表")
        void toDocDetailResponse_shouldIncludeChunks() {
            KbDocument doc = buildDoc("doc-001", "kb-001");
            doc.setErrorMessage("无错误");
            doc.setMetadataJson("{\"author\":\"test\"}");

            List<KbChunk> chunks = List.of(
                    buildChunk(0, "这是第一段内容"),
                    buildChunk(1, "这是第二段内容")
            );

            DocDetailResponse resp = converter.toDocDetailResponse(doc, chunks);

            assertEquals("doc-001", resp.getDocId());
            assertEquals("kb-001", resp.getKbId());
            assertEquals("test.pdf", resp.getFileName());
            assertEquals("无错误", resp.getErrorMessage());
            assertEquals("{\"author\":\"test\"}", resp.getMetadataJson());

            assertNotNull(resp.getChunks());
            assertEquals(2, resp.getChunks().size());

            DocDetailResponse.ChunkPreview preview0 = resp.getChunks().get(0);
            assertEquals(0, preview0.getChunkIndex());
            assertEquals("这是第一段内容", preview0.getContent());
            assertEquals(7, preview0.getTokenCount());

            DocDetailResponse.ChunkPreview preview1 = resp.getChunks().get(1);
            assertEquals(1, preview1.getChunkIndex());
            assertEquals("这是第二段内容", preview1.getContent());
            assertEquals(7, preview1.getTokenCount());
        }

        @Test
        @DisplayName("空分块列表 → chunks 为空列表")
        void toDocDetailResponse_emptyChunks_shouldReturnEmptyList() {
            KbDocument doc = buildDoc("doc-001", "kb-001");

            DocDetailResponse resp = converter.toDocDetailResponse(doc, Collections.emptyList());

            assertNotNull(resp.getChunks());
            assertTrue(resp.getChunks().isEmpty());
        }
    }
}
