/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.tool.ResumeEvaluateResult;
import com.tutorial.offerpilot.dto.tool.ResumeParseResult;
import com.tutorial.offerpilot.service.ingestion.DocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历服务 — 解析（正则提取结构化字段）+ 评估指导（由 LLM 生成评分和建议）。
 *
 * <p>职责划分：
 * <ul>
 *   <li>parseResume：正则提取姓名/邮箱/电话/教育/项目/经验 — 工具本分</li>
 *   <li>evaluateResume：返回简历原文 + 评估指导 — LLM 据此自由生成评分和建议</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "1[3-9]\\d{9}");
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^([\\u4e00-\\u9fa5]{2,4})\\s*$", Pattern.MULTILINE);

    private final DocumentParser documentParser;

    // ======================== 简历解析（保留） ========================

    /**
     * 解析简历文件，调用 DocumentParser 提取文本后结构化解析。
     */
    public ResumeParseResult parseResume(String pdfUrl) {
        log.info("Parsing resume: url={}", pdfUrl);

        if (pdfUrl == null || pdfUrl.isBlank()) {
            return new ResumeParseResult("", "", "", List.of(), List.of(), List.of(), List.of());
        }

        String extractedText = extractText(pdfUrl);

        String name = extractName(extractedText);
        String email = extractEmail(extractedText);
        String phone = extractPhone(extractedText);
        List<String> education = extractEducation(extractedText);
        List<String> projects = extractProjects(extractedText);
        List<String> skills = extractSkills(extractedText);
        List<String> experience = extractExperience(extractedText);

        log.info("Resume parsed: name={}, email={}, skills={}", name, email, skills.size());
        return new ResumeParseResult(name, email, phone, education, projects, skills, experience);
    }

    // ======================== 简历评估（改为 LLM 模式） ========================

    /**
     * 返回简历原文 + 评估指导，由 LLM 据此自由生成评分和改进建议。
     * 不再硬编码任何评分规则、关键词匹配或评语模板。
     */
    public ResumeEvaluateResult evaluateResume(String resumeText) {
        log.info("Evaluating resume: textLen={}", resumeText != null ? resumeText.length() : 0);

        if (resumeText == null || resumeText.isBlank()) {
            return new ResumeEvaluateResult(null,
                    "请上传简历文件以进行评估。",
                    null, null, List.of(), List.of(), List.of());
        }

        String guidance = buildEvaluationGuidance(resumeText);

        log.info("Resume evaluation guidance generated, textLen={}", resumeText.length());
        return new ResumeEvaluateResult(resumeText, guidance,
                null, null, List.of(), List.of(), List.of());
    }

    /**
     * 构建简历评估指导文本，不包含任何硬编码评分规则或建议模板。
     */
    private String buildEvaluationGuidance(String resumeText) {
        return String.format("""
                请评估以下简历的质量，给出综合评分（0-100）和改进建议：

                【简历内容】
                %s

                请从以下维度评估：
                1. 内容完整性：是否包含教育背景、工作/项目经历、技能等必要模块
                2. 表达质量：描述是否清晰具体，是否使用了量化成果和具体案例
                3. 岗位匹配度：内容是否聚焦、是否突出了核心竞争力

                请输出格式：
                - 综合评分：XX分 — [总体评价]
                - 优点：[列出1-3个优点]
                - 不足：[列出1-3个需要改进的地方]
                - 改进建议：[给出具体可操作的改进建议，针对性优化]
                """, resumeText);
    }

    // ======================== 文本提取（保留） ========================

    private String extractText(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            log.info("Remote URL detected, using text-mode extraction: {}", url);
            return extractTextFromUrl(url);
        }
        try {
            Path path = Path.of(url);
            if (!Files.exists(path)) {
                log.warn("File not found: {}, falling back to text-mode", url);
                return extractTextFromUrl(url);
            }
            String fileType = detectFileType(url);
            String text = documentParser.parse(url, fileType);
            log.info("DocumentParser extracted {} chars from {}", text.length(), url);
            return text;
        } catch (IOException e) {
            log.warn("DocumentParser failed for '{}': {}, falling back to text-mode", url, e.getMessage());
            return extractTextFromUrl(url);
        }
    }

    private String detectFileType(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "markdown";
        if (lower.endsWith(".txt")) return "txt";
        return "txt";
    }

    private String extractTextFromUrl(String url) {
        if (url.contains("pdf")) {
            return "[PDF 简历文件] " + url + "\n需要下载后使用 PDFBox 提取文本内容";
        }
        if (url.contains("docx") || url.contains("doc")) {
            return "[Word 简历文件] " + url + "\n需要下载后使用 POI 提取文本内容";
        }
        return url;
    }

    // ======================== 正则提取（保留） ========================

    private String extractName(String text) {
        Matcher matcher = NAME_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private String extractPhone(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private List<String> extractEducation(String text) {
        List<String> education = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.contains("大学") || line.contains("学院") || line.contains("本科")
                    || line.contains("硕士") || line.contains("博士") || line.contains("学历")) {
                education.add(line.trim());
            }
        }
        return education;
    }

    private List<String> extractProjects(String text) {
        List<String> projects = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.contains("项目") || line.contains("实习") || line.contains("开源")) {
                projects.add(line.trim());
            }
        }
        return projects;
    }

    /**
     * 提取技能关键词 — 进行广泛的模式匹配，而非硬编码特定技术栈。
     * 匹配常见技能表述模式："技能：XXX"、"熟练掌握XXX"、"精通XXX"。
     */
    private List<String> extractSkills(String text) {
        List<String> skills = new ArrayList<>();
        Pattern skillPattern = Pattern.compile(
                "(?:技能[：:]\\s*|熟练掌握|精通|熟悉|了解|掌握)([\\u4e00-\\u9fa5a-zA-Z+#、，,\\s]+?)(?:[。；;]|$)");
        Matcher matcher = skillPattern.matcher(text);
        while (matcher.find()) {
            String group = matcher.group(1).trim();
            if (!group.isBlank() && group.length() < 100) {
                skills.add(group);
            }
        }
        // 也匹配独立技能行 "Java、Python、Figma" 等
        if (skills.isEmpty()) {
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.length() > 2 && trimmed.length() < 120
                        && !trimmed.startsWith("http")
                        && !trimmed.contains("：") && !trimmed.contains(":")) {
                    skills.add(trimmed);
                }
            }
        }
        return skills.stream().distinct().limit(10).toList();
    }

    private List<String> extractExperience(String text) {
        List<String> experience = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.contains("公司") || line.contains("工作") || line.contains("担任")
                    || line.contains("负责") || line.contains("经验")) {
                experience.add(line.trim());
            }
        }
        return experience;
    }
}
