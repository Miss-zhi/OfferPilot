/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.AbstractControllerIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("FileUploadController 集成测试")
class FileUploadControllerIT extends AbstractControllerIT {

    @BeforeEach
    void setUpRateLimit() {
        // Configure RateLimitService mock to allow all requests
        when(rateLimitService.tryAcquireUpload(anyString())).thenReturn(true);
    }

    // ==================== POST /api/v1/offerpilot/upload ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/upload")
    class UploadTests {

        @Test
        @DisplayName("未认证 → 401 Unauthorized")
        void noAuth_shouldReturn401() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "dummy content".getBytes());

            mockMvc.perform(multipart("/api/v1/offerpilot/upload").file(file))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("认证用户上传 PDF → 200 + 文件信息")
        void authenticated_uploadPdf_shouldReturn200() throws Exception {
            String token = registerUserAndGetToken("uploaduser");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "resume.pdf", "application/pdf", "PDF content".getBytes());

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .file(file)
                            .param("type", "resume")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.filePath").isNotEmpty())
                    .andExpect(jsonPath("$.data.fileType").value("resume"))
                    .andExpect(jsonPath("$.data.size").value(11));
        }

        @Test
        @DisplayName("上传 txt 文件 → 200")
        void upload_txt_shouldReturn200() throws Exception {
            String token = registerUserAndGetToken("uploadtxt");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "notes.txt", "text/plain", "Hello World".getBytes());

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .file(file)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.filePath").isNotEmpty())
                    .andExpect(jsonPath("$.data.fileType").value("general"));
        }

        @Test
        @DisplayName("无文件上传 → 500 服务器错误")
        void noFile_shouldReturn500() throws Exception {
            String token = registerUserAndGetToken("uploadnofile");

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isInternalServerError());
        }
    }
}
