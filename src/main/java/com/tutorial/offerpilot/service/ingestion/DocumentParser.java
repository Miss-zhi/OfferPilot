/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * 多格式文档解析器。
 * 支持 markdown、txt、pdf、docx 格式，提取纯文本内容。
 */
@Slf4j
@Component
public class DocumentParser {

    /**
     * 解析文件，提取纯文本。
     * 支持格式：markdown, pdf, txt, docx
     */
    public String parse(String filePath, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "markdown", "md" -> parseMarkdown(filePath);
            case "txt" -> java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
            case "pdf" -> parsePdf(filePath);
            case "docx" -> parseDocx(filePath);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + fileType);
        };
    }

    private String parseMarkdown(String filePath) throws IOException {
        String content = java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
        return content.replaceAll("#{1,6}\\s+", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1")
                .replaceAll("```[\\s\\S]*?```", "")
                .trim();
    }

    private String parsePdf(String filePath) throws IOException {
        log.info("Parsing PDF: {}", filePath);
        try (PDDocument document = Loader.loadPDF(new java.io.File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document).trim();
            log.info("PDF parsed: {} chars from {}", text.length(), filePath);
            return text;
        }
    }

    private String parseDocx(String filePath) throws IOException {
        log.info("Parsing DOCX: {}", filePath);
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();

            // 提取段落文本
            document.getParagraphs().forEach(p -> {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            });

            // 提取表格文本
            document.getTables().forEach(t -> {
                t.getRows().forEach(r -> {
                    r.getTableCells().forEach(c -> sb.append(c.getText()).append("\t"));
                    sb.append("\n");
                });
            });

            String text = sb.toString().trim();
            log.info("DOCX parsed: {} chars from {}", text.length(), filePath);
            return text;
        }
    }
}
