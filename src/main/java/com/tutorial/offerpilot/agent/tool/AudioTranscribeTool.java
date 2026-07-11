/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.agent.tool;

import com.tutorial.offerpilot.dto.tool.TranscribeResult;
import com.tutorial.offerpilot.service.TranscriptionService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class AudioTranscribeTool {

    private final TranscriptionService transcriptionService;

    public AudioTranscribeTool(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @Tool(name = "transcribe_audio", description = "转写面试录音文件为文本，支持常见音频格式")
    public TranscribeResult transcribe(
            @ToolParam(name = "file_path", description = "音频文件路径") String filePath) {
        log.info("transcribe_audio called: path={}", filePath);

        if (filePath == null || filePath.isBlank()) {
            log.warn("transcribe_audio: empty file path");
            return new TranscribeResult("文件路径为空，无法转写", 0, 0);
        }

        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                log.warn("transcribe_audio: file not found: {}", filePath);
                return new TranscribeResult("文件不存在: " + filePath, 0, 0);
            }

            // 调用 DashScope Paraformer 真实转写
            String content = transcriptionService.transcribe(filePath);
            int durationSeconds = estimateDuration(content);
            int questionCount = countQuestions(content);

            log.info("transcribe_audio result: chars={}, duration={}s, questions={}",
                    content.length(), durationSeconds, questionCount);
            return new TranscribeResult(content, durationSeconds, questionCount);
        } catch (IOException e) {
            log.warn("transcribe_audio: failed: {}", e.getMessage());
            return new TranscribeResult("转写失败: " + e.getMessage(), 0, 0);
        }
    }

    /**
     * 根据文本长度估算音频时长（假设中文语速约 250 字/分钟）。
     */
    private int estimateDuration(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int charCount = text.replaceAll("\\s+", "").length();
        return Math.max(1, charCount * 60 / 250);
    }

    /**
     * 统计文本中可能的面试问题数量（按问号/问题句识别）。
     */
    private int countQuestions(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.endsWith("?") || trimmed.endsWith("？")) {
                count++;
            }
        }
        return count;
    }
}
