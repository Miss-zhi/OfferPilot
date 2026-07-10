/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.converter;

import com.tutorial.offerpilot.dto.kb.DocDetailResponse;
import com.tutorial.offerpilot.dto.kb.DocResponse;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.entity.KbChunk;
import com.tutorial.offerpilot.entity.KbDocument;
import com.tutorial.offerpilot.entity.KbKnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class KbConverter {

    public KbResponse toResponse(KbKnowledgeBase kb) {
        KbResponse resp = new KbResponse();
        resp.setKbId(kb.getKbId());
        resp.setName(kb.getName());
        resp.setDescription(kb.getDescription());
        resp.setCategory(kb.getCategory());
        resp.setVisibility(kb.getVisibility());
        resp.setStatus(kb.getStatus());
        resp.setDocumentCount(kb.getDocumentCount());
        resp.setChunkCount(kb.getChunkCount());
        return resp;
    }

    public DocResponse toDocResponse(KbDocument doc) {
        DocResponse resp = new DocResponse();
        resp.setDocId(doc.getDocId());
        resp.setKbId(doc.getKbId());
        resp.setFileName(doc.getFileName());
        resp.setFileType(doc.getFileType());
        resp.setFileSize(doc.getFileSize());
        resp.setChunkCount(doc.getChunkCount());
        resp.setChunkStrategy(doc.getChunkStrategy());
        resp.setStatus(doc.getStatus());
        resp.setProgress(doc.getProgress());
        resp.setTags(doc.getTags());
        resp.setUploadedAt(doc.getUploadedAt());
        resp.setIndexedAt(doc.getIndexedAt());
        return resp;
    }

    public DocDetailResponse toDocDetailResponse(KbDocument doc, List<KbChunk> chunks) {
        DocDetailResponse resp = new DocDetailResponse();
        resp.setDocId(doc.getDocId());
        resp.setKbId(doc.getKbId());
        resp.setFileName(doc.getFileName());
        resp.setFileType(doc.getFileType());
        resp.setFileSize(doc.getFileSize());
        resp.setChunkCount(doc.getChunkCount());
        resp.setChunkStrategy(doc.getChunkStrategy());
        resp.setStatus(doc.getStatus());
        resp.setProgress(doc.getProgress());
        resp.setTags(doc.getTags());
        resp.setUploadedAt(doc.getUploadedAt());
        resp.setIndexedAt(doc.getIndexedAt());
        resp.setErrorMessage(doc.getErrorMessage());
        resp.setMetadataJson(doc.getMetadataJson());
        resp.setChunks(chunks.stream()
                .map(chunk -> {
                    DocDetailResponse.ChunkPreview preview = new DocDetailResponse.ChunkPreview();
                    preview.setChunkIndex(chunk.getChunkIndex());
                    preview.setContent(chunk.getContent());
                    preview.setTokenCount(chunk.getTokenCount());
                    return preview;
                })
                .collect(Collectors.toList()));
        return resp;
    }
}
