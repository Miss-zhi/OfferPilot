/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentChunker 单元测试")
class DocumentChunkerTest {

    private DocumentChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new DocumentChunker();
        // 手动设置 @Value 字段（Spring 未启动时为空）
        setField(chunker, "defaultChunkSize", 500);
        setField(chunker, "defaultChunkOverlap", 50);
    }

    private void setField(Object target, String fieldName, int value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== BY_QUESTION 策略 ====================

    @Nested
    @DisplayName("BY_QUESTION 策略")
    class ByQuestionTests {

        @Test
        @DisplayName("按 --- 分隔 → 多个 chunk")
        void chunkByQuestion_shouldSplitBySeparator() {
            String text = "问题一：这是一段足够长的文本内容用于测试分块功能是否正常工作".repeat(2)
                    + "\n---\n"
                    + "问题二：这是第二段足够长的文本内容用于测试分块功能是否正常工作".repeat(2)
                    + "\n---\n"
                    + "问题三：这是第三段足够长的文本内容用于测试分块功能是否正常工作".repeat(2);
            List<String> chunks = chunker.chunk(text, "BY_QUESTION");

            assertEquals(3, chunks.size());
            assertTrue(chunks.get(0).contains("问题一"));
            assertTrue(chunks.get(1).contains("问题二"));
        }

        @Test
        @DisplayName("过短的片段被过滤")
        void chunkByQuestion_tooShort_shouldBeFiltered() {
            String text = "问题一：这是一段足够长的文本内容用于测试分块功能".repeat(2)
                    + "\n---\nab\n---\n"
                    + "问题三：这是第三段足够长的文本内容用于测试分块功能".repeat(2);
            List<String> chunks = chunker.chunk(text, "BY_QUESTION");

            assertEquals(2, chunks.size());
        }
    }

    // ==================== BY_HEADING 策略 ====================

    @Nested
    @DisplayName("BY_HEADING 策略")
    class ByHeadingTests {

        @Test
        @DisplayName("按 Markdown 标题分隔")
        void chunkByHeading_shouldSplitByHeadings() {
            String text = """
                    ## 标题一
                    这里是一段较长的文本内容，用来测试标题分割功能是否正常工作。
                    这里是一段较长的文本内容，用来测试标题分割功能是否正常工作。
                    这里是一段较长的文本内容，用来测试标题分割功能是否正常工作。
                    这里是一段较长的文本内容，用来测试标题分割功能是否正常工作。
                    ## 标题二
                    这是第二段很长很长的文本内容，用于测试标题分割功能，确保足够长度。
                    这是第二段很长很长的文本内容，用于测试标题分割功能，确保足够长度。
                    这是第二段很长很长的文本内容，用于测试标题分割功能，确保足够长度。
                    这是第二段很长很长的文本内容，用于测试标题分割功能，确保足够长度。
                    """;
            List<String> chunks = chunker.chunk(text, "BY_HEADING");

            assertEquals(2, chunks.size());
            assertTrue(chunks.get(0).contains("标题一"));
            assertTrue(chunks.get(1).contains("标题二"));
        }
    }

    // ==================== BY_SIZE 策略 ====================

    @Nested
    @DisplayName("BY_SIZE 策略")
    class BySizeTests {

        @Test
        @DisplayName("长文本 → 按固定大小分割")
        void chunkBySize_shouldSplitBySize() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("0123456789".repeat(50)); // 500 chars per segment
            }
            List<String> chunks = chunker.chunk(sb.toString(), "BY_SIZE");

            assertTrue(chunks.size() >= 2);
        }
    }

    // ==================== AUTO 策略 ====================

    @Nested
    @DisplayName("AUTO 策略")
    class AutoStrategyTests {

        @Test
        @DisplayName("多个 --- 分隔符 → 自动选择 BY_QUESTION")
        void autoChunk_withSeparators_shouldUseByQuestion() {
            String text = "AAAA".repeat(10) + "\n---\n"
                    + "BBBB".repeat(10) + "\n---\n"
                    + "CCCC".repeat(10) + "\n---\n"
                    + "DDDD".repeat(10);
            List<String> chunks = chunker.chunk(text, "AUTO");

            assertEquals(4, chunks.size());
        }

        @Test
        @DisplayName("无分隔符 → 回退到 BY_SIZE")
        void autoChunk_noSeparators_shouldFallbackToSize() {
            String text = "普通文本没有分隔符也没有标题".repeat(50);
            List<String> chunks = chunker.chunk(text, "AUTO");

            assertFalse(chunks.isEmpty());
        }

        @Test
        @DisplayName("null 策略 → 默认 AUTO")
        void chunk_nullStrategy_shouldDefaultToAuto() {
            String text = "AAAA".repeat(10) + "\n---\n"
                    + "BBBB".repeat(10) + "\n---\n"
                    + "CCCC".repeat(10) + "\n---\n"
                    + "DDDD".repeat(10);
            List<String> chunks = chunker.chunk(text, null);

            assertEquals(4, chunks.size());
        }
    }
}
