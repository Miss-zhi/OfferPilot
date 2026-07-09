/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.dto.kb.CreateKbRequest;
import com.tutorial.offerpilot.dto.kb.KbResponse;
import com.tutorial.offerpilot.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/kb")
@RequiredArgsConstructor
public class KnowledgeBaseAdminController {

    private final KnowledgeBaseService kbService;

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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteKnowledgeBase(@PathVariable String kbId) {
        kbService.deleteKnowledgeBase(kbId);
        return ResponseEntity.noContent().build();
    }

    private String getUserId(UserDetails userDetails) {
        return userDetails.getUsername();
    }
}
