/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tutorial.offerpilot.AbstractServiceIT;
import com.tutorial.offerpilot.exception.FileUploadException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@DisplayName("FileService 集成测试")
class FileServiceIT extends AbstractServiceIT {

    @Autowired
    private FileService fileService;

    @AfterEach
    void cleanupTestFiles() throws IOException {
        Path testDir = Path.of("./target/test-uploads/test-files");
        if (Files.exists(testDir)) {
            try (var stream = Files.walk(testDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                        });
            }
        }
    }

    // ==================== saveFile ====================

    @Nested
    @DisplayName("saveFile")
    class SaveFileTests {

        @Test
        @DisplayName("PDF文件 → 保存成功返回路径")
        void saveFile_validPdf_shouldReturnPath() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "resume.pdf", "application/pdf",
                    "test pdf content".getBytes());

            String path = fileService.saveFile(file, "test-files");

            assertNotNull(path);
            assertTrue(path.endsWith(".pdf"));
            assertTrue(Files.exists(Path.of(path)));
        }

        @Test
        @DisplayName("不支持的类型 .exe → 抛出 FileUploadException")
        void saveFile_unsupportedType_shouldThrowException() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "virus.exe", "application/octet-stream",
                    "malicious".getBytes());

            assertThrows(FileUploadException.class,
                    () -> fileService.saveFile(file, "test-files"));
        }

        @Test
        @DisplayName("无扩展名文件 → 保存成功（目前行为）")
        void saveFile_noExtension_shouldSave() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "noext", "application/octet-stream",
                    "content".getBytes());

            // Current behavior: files without extension are saved
            String path = fileService.saveFile(file, "test-files");

            assertNotNull(path);
            assertTrue(Files.exists(Path.of(path)));
        }

        @Test
        @DisplayName("TXT文件 → 保存成功")
        void saveFile_validTxt_shouldReturnPath() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "notes.txt", "text/plain",
                    "hello world".getBytes());

            String path = fileService.saveFile(file, "test-files");

            assertNotNull(path);
            assertTrue(path.endsWith(".txt"));
            assertTrue(Files.exists(Path.of(path)));
        }
    }
}
