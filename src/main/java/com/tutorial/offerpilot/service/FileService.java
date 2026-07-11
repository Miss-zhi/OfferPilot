/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.exception.FileUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.allowed-file-types:pdf,mp3,wav,m4a,txt,md,docx}")
    private String allowedFileTypes;

    public String saveFile(MultipartFile file, String subDir) {
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1)
                : "";

        if (!allowedFileTypes.contains(ext.toLowerCase())) {
            throw new FileUploadException("不支持的文件类型: " + ext);
        }

        String fileName = UUID.randomUUID() + "." + ext;
        // 使用绝对路径，避免 Tomcat 嵌入式环境下相对路径解析到临时目录
        Path targetDir = Paths.get(uploadDir, subDir).toAbsolutePath();
        try {
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(fileName);
            file.transferTo(targetPath.toFile());
            log.info("File saved: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            throw new FileUploadException("文件保存失败: " + e.getMessage());
        }
    }
}
