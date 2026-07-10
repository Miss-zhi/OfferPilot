/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.DocDetailResponse;
import com.tutorial.offerpilot.dto.kb.DocProgress;
import com.tutorial.offerpilot.dto.kb.DocResponse;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.dto.kb.KbStatsResponse;
import com.tutorial.offerpilot.dto.kb.SearchTestRequest;
import com.tutorial.offerpilot.dto.kb.SearchTestResponse;
import com.tutorial.offerpilot.service.FileService;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final FileService fileService;

    @PostMapping
    public ResponseEntity<ApiResponse<KbResponse>> createKnowledgeBase(
            @RequestBody @Valid CreateKbRequest req,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Create KB request: name={}, userId={}", req.getName(), userId);
        KbResponse kb = kbService.createKnowledgeBase(req, userId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(kb));
    }

    @GetMapping
    public ApiResponse<List<KbResponse>> listKnowledgeBases(
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        return ApiResponse.success(kbService.listKnowledgeBases(userId, currentUser));
    }

    @DeleteMapping("/{kbId}")
    public ResponseEntity<Void> deleteKnowledgeBase(
            @PathVariable String kbId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        kbService.deleteKnowledgeBase(kbId, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }

    // ======================== 以下是新增的 KB 文档管理端点 ========================

    /** 知识库统计 */
    @GetMapping("/{kbId}/stats")
    public ApiResponse<KbStatsResponse> getKbStats(
            @PathVariable String kbId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Get KB stats: kbId={}", kbId);
        return ApiResponse.success(kbService.getKbStats(kbId, userId, currentUser));
    }

    /** 文档列表 */
    @GetMapping("/{kbId}/docs")
    public ApiResponse<List<DocResponse>> listDocs(
            @PathVariable String kbId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("List docs: kbId={}", kbId);
        return ApiResponse.success(kbService.listDocs(kbId, userId, currentUser));
    }

    /** 上传文档 */
    @PostMapping("/{kbId}/docs")
    public ResponseEntity<ApiResponse<DocResponse>> uploadDoc(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "chunkStrategy", defaultValue = "AUTO") String chunkStrategy,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Upload doc: kbId={}, fileName={}, strategy={}", kbId, file.getOriginalFilename(), chunkStrategy);

        String filePath = fileService.saveFile(file, "kb-docs");
        String originalName = file.getOriginalFilename();
        String fileType = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1)
                : "";

        DocResponse doc = kbService.createDoc(kbId, originalName, filePath, fileType,
                file.getSize(), chunkStrategy, userId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(doc));
    }

    /** 文档详情 */
    @GetMapping("/{kbId}/docs/{docId}")
    public ApiResponse<DocDetailResponse> getDocDetail(
            @PathVariable String kbId,
            @PathVariable String docId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Get doc detail: kbId={}, docId={}", kbId, docId);
        return ApiResponse.success(kbService.getDocDetail(kbId, docId, userId, currentUser));
    }

    /** 删除文档 */
    @DeleteMapping("/{kbId}/docs/{docId}")
    public ResponseEntity<Void> deleteDoc(
            @PathVariable String kbId,
            @PathVariable String docId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Delete doc: kbId={}, docId={}", kbId, docId);
        kbService.deleteDoc(kbId, docId, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    /** 入库进度 */
    @GetMapping("/{kbId}/docs/{docId}/progress")
    public ApiResponse<DocProgress> getDocProgress(
            @PathVariable String kbId,
            @PathVariable String docId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Get doc progress: kbId={}, docId={}", kbId, docId);
        return ApiResponse.success(kbService.getDocProgress(kbId, docId, userId, currentUser));
    }

    /** 重新索引 */
    @PostMapping("/{kbId}/docs/{docId}/reindex")
    public ApiResponse<Void> reindexDoc(
            @PathVariable String kbId,
            @PathVariable String docId,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Reindex doc: kbId={}, docId={}", kbId, docId);
        kbService.reindexDoc(kbId, docId, userId, currentUser);
        return ApiResponse.success();
    }

    /** 检索测试 */
    @PostMapping("/{kbId}/search")
    public ApiResponse<SearchTestResponse> search(
            @PathVariable String kbId,
            @RequestBody @Valid SearchTestRequest req,
            @AuthenticationPrincipal UserDetails currentUser) {
        String userId = getUserId(currentUser);
        log.info("Search KB: kbId={}, query={}, topK={}", kbId, req.getQuery(), req.getTopK());
        return ApiResponse.success(kbService.searchKb(kbId, req.getQuery(), req.getFilterExpr(),
                req.getTopK(), userId, currentUser));
    }
}