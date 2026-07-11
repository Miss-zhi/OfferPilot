/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorial.offerpilot.config.AgentScopeProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 录音转写服务。
 * 默认调用 DashScope Paraformer（OpenAI 兼容 /v1/audio/transcriptions 端点）。
 * 支持通过 agentscope.transcription.* 独立配置，未配置 api-key 时回退 model.api-key。
 */
@Slf4j
@Service
public class TranscriptionService {

    private final String apiKey;
    private final String model;
    private final String transcriptionsUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TranscriptionService(AgentScopeProperties properties) {
        AgentScopeProperties.TranscriptionConfig config = properties.getTranscription();

        // API Key 优先级: transcription.api-key > model.api-key（回退兼容）
        String transcriptionApiKey = config.getApiKey();
        if (transcriptionApiKey != null && !transcriptionApiKey.isBlank()) {
            this.apiKey = transcriptionApiKey;
        } else {
            this.apiKey = properties.getModel().getApiKey();
            log.info("TranscriptionService using fallback api-key from agentscope.model.api-key");
        }

        this.model = config.getModel();
        this.transcriptionsUrl = config.getBaseUrl() + "/audio/transcriptions";
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("TranscriptionService initialized: model={}, url={}", model, transcriptionsUrl);
    }

    /**
     * 将音频文件转写为文本。
     *
     * @param filePath 音频文件路径（支持 mp3/wav/m4a 等格式）
     * @return 转写结果文本
     */
    public String transcribe(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + filePath);
        }

        byte[] audioBytes = Files.readAllBytes(path);
        String fileName = path.getFileName().toString();
        String mimeType = inferMimeType(fileName);

        log.info("Transcribing audio: file={}, size={}KB, mimeType={}",
                fileName, audioBytes.length / 1024, mimeType);

        RequestBody fileBody = RequestBody.create(audioBytes, MediaType.parse(mimeType));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .addFormDataPart("model", model)
                .build();

        Request request = new Request.Builder()
                .url(transcriptionsUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("Transcription API error: status={}, body={}", response.code(), responseBody);
                throw new IOException("转写 API 返回错误: " + response.code() + " " + responseBody);
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("text").asText();

            if (text.isEmpty()) {
                log.warn("Transcription returned empty text: body={}", responseBody);
                return "";
            }

            log.info("Transcription success: file={}, textLength={}", fileName, text.length());
            return text;
        }
    }

    /**
     * 根据文件名推断 MIME 类型。
     */
    private String inferMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".webm")) return "audio/webm";
        return "audio/mpeg"; // 默认
    }
}
