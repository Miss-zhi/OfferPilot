/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.common.ApiResponse;
import com.tutorial.offerpilot.service.FileService;
import com.tutorial.offerpilot.service.RateLimitService;
import com.tutorial.offerpilot.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/offerpilot/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileService fileService;
    private final RateLimitService rateLimitService;

    @PostMapping
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "general") String type,
            @AuthenticationPrincipal UserDetails currentUser) {

        String userId = currentUser.getUsername();
        if (!rateLimitService.tryAcquireUpload(userId)) {
            throw new RateLimitException("上传频率过高，请稍后再试");
        }

        String filePath = fileService.saveFile(file, type);
        log.info("File uploaded: userId={}, type={}, path={}", userId, type, filePath);

        return ApiResponse.success(Map.of(
                "filePath", filePath,
                "fileType", type,
                "size", file.getSize()
        ));
    }
}
