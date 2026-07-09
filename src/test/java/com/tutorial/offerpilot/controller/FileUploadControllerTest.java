/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.controller;

import com.tutorial.offerpilot.exception.GlobalExceptionHandler;
import com.tutorial.offerpilot.exception.RateLimitException;
import com.tutorial.offerpilot.service.FileService;
import com.tutorial.offerpilot.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadController Web 层测试")
class FileUploadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FileService fileService;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private FileUploadController controller;

    private static final String USER_ID = "testuser";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        User user = new User(USER_ID, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== POST /api/v1/offerpilot/upload ====================

    @Nested
    @DisplayName("POST /api/v1/offerpilot/upload")
    class UploadTests {

        @Test
        @DisplayName("正常上传 → 200 + 文件路径")
        void upload_shouldReturn200() throws Exception {
            when(rateLimitService.tryAcquireUpload(USER_ID)).thenReturn(true);
            when(fileService.saveFile(any(), eq("resume"))).thenReturn("/uploads/resume/test.pdf");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "dummy content".getBytes());

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .file(file)
                            .param("type", "resume"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.filePath").value("/uploads/resume/test.pdf"))
                    .andExpect(jsonPath("$.data.fileType").value("resume"))
                    .andExpect(jsonPath("$.data.size").value(13));

            verify(rateLimitService).tryAcquireUpload(USER_ID);
            verify(fileService).saveFile(any(), eq("resume"));
        }

        @Test
        @DisplayName("无 type 参数 → 使用默认 general")
        void upload_defaultType_shouldUseGeneral() throws Exception {
            when(rateLimitService.tryAcquireUpload(USER_ID)).thenReturn(true);
            when(fileService.saveFile(any(), eq("general"))).thenReturn("/uploads/general/file.txt");

            MockMultipartFile file = new MockMultipartFile(
                    "file", "file.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fileType").value("general"))
                    .andExpect(jsonPath("$.data.size").value(5));

            verify(fileService).saveFile(any(), eq("general"));
        }

        @Test
        @DisplayName("限流触发 → 429 RateLimitException")
        void upload_rateLimited_shouldReturn429() throws Exception {
            when(rateLimitService.tryAcquireUpload(USER_ID)).thenReturn(false);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .file(file))
                    .andExpect(status().is(429))
                    .andExpect(jsonPath("$.code").value(429))
                    .andExpect(jsonPath("$.message").value("上传频率过高，请稍后再试"));

            verify(fileService, never()).saveFile(any(), anyString());
        }

        @Test
        @DisplayName("文件存储失败 → 500 内部错误")
        void upload_saveFailure_shouldReturn500() throws Exception {
            when(rateLimitService.tryAcquireUpload(USER_ID)).thenReturn(true);
            when(fileService.saveFile(any(), anyString()))
                    .thenThrow(new RuntimeException("磁盘已满"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .file(file))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
        }

        @Test
        @DisplayName("多文件类型 → 正确映射 type 参数")
        void upload_multipleTypes_shouldMapCorrectly() throws Exception {
            String[] types = {"resume", "jobdesc", "coverletter", "general"};
            for (String type : types) {
                when(rateLimitService.tryAcquireUpload(USER_ID)).thenReturn(true);
                when(fileService.saveFile(any(), eq(type))).thenReturn("/uploads/" + type + "/file.pdf");

                MockMultipartFile file = new MockMultipartFile(
                        "file", "file.pdf", "application/pdf", "x".getBytes());

                mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                                .file(file)
                                .param("type", type))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.fileType").value(type));
            }
            verify(fileService, times(4)).saveFile(any(), anyString());
        }

        @Test
        @DisplayName("大文件上传 → 正确返回 size")
        void upload_largeFile_shouldReturnCorrectSize() throws Exception {
            when(rateLimitService.tryAcquireUpload(USER_ID)).thenReturn(true);
            when(fileService.saveFile(any(), eq("general"))).thenReturn("/uploads/large.bin");
            byte[] largeContent = new byte[1024 * 1024]; // 1MB

            MockMultipartFile file = new MockMultipartFile(
                    "file", "large.bin", "application/octet-stream", largeContent);

            mockMvc.perform(multipart("/api/v1/offerpilot/upload")
                            .file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(1048576));
        }
    }
}
