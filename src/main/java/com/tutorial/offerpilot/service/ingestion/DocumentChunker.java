/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DocumentChunker {

    @Value("${agentscope.knowledge.chunk-size:500}")
    private int defaultChunkSize;

    @Value("${agentscope.knowledge.chunk-overlap:50}")
    private int defaultChunkOverlap;

    public List<String> chunk(String text, String strategy) {
        return switch (strategy != null ? strategy : "AUTO") {
            case "BY_QUESTION" -> chunkByQuestion(text);
            case "BY_HEADING" -> chunkByHeading(text);
            case "BY_SIZE" -> chunkBySize(text, defaultChunkSize, defaultChunkOverlap);
            case "AUTO" -> autoChunk(text);
            default -> chunkBySize(text, defaultChunkSize, defaultChunkOverlap);
        };
    }

    private List<String> autoChunk(String text) {
        long separatorCount = text.lines().filter(line -> line.trim().equals("---")).count();
        if (separatorCount >= 3) return chunkByQuestion(text);

        long headingCount = text.lines().filter(line -> line.matches("^#{1,3}\\s+.*")).count();
        if (headingCount >= 3) return chunkByHeading(text);

        return chunkBySize(text, defaultChunkSize, defaultChunkOverlap);
    }

    private List<String> chunkByQuestion(String text) {
        return Arrays.stream(text.split("---"))
                .map(String::trim)
                .filter(s -> s.length() > 20)
                .collect(Collectors.toList());
    }

    private List<String> chunkByHeading(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.matches("^#{1,3}\\s+.*") && current.length() > 100) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (current.length() > 100) chunks.add(current.toString().trim());
        return chunks;
    }

    private List<String> chunkBySize(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
        }
        return chunks;
    }
}
