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

    /**
     * 评估简历质量并给出改进建议。
     */
    public ResumeEvaluateResult evaluateResume(String resumeText) {
        log.info("Evaluating resume: textLen={}", resumeText != null ? resumeText.length() : 0);

        if (resumeText == null || resumeText.isBlank()) {
            return new ResumeEvaluateResult(0, "简历内容为空", List.of(),
                    List.of("请提供简历文本"), List.of("上传简历文件进行评估"));
        }

        int overallScore = calculateOverallScore(resumeText);
        String summary = generateSummary(overallScore);
        List<String> strengths = extractStrengths(resumeText);
        List<String> weaknesses = extractWeaknesses(resumeText);
        List<String> suggestions = generateSuggestions(resumeText, weaknesses);

        log.info("Resume evaluated: overallScore={}", overallScore);
        return new ResumeEvaluateResult(overallScore, summary, strengths, weaknesses, suggestions);
    }

    /**
     * 调用 DocumentParser 提取文本（本地文件），远程 URL 则降级为文本模式解析。
     */
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

    private String extractName(String text) {
        Matcher matcher = NAME_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private String extractPhone(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private List<String> extractEducation(String text) {
        List<String> education = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.contains("大学") || line.contains("学院") || line.contains("本科")
                    || line.contains("硕士") || line.contains("博士") || line.contains("学历")) {
                education.add(line.trim());
            }
        }
        return education;
    }

    private List<String> extractProjects(String text) {
        List<String> projects = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.contains("项目") || line.contains("实习") || line.contains("开源")) {
                projects.add(line.trim());
            }
        }
        return projects;
    }

    private List<String> extractSkills(String text) {
        List<String> skills = new ArrayList<>();
        String[] keywords = {"Java", "Python", "Go", "JavaScript", "TypeScript", "React", "Vue",
                "Spring", "MySQL", "Redis", "Docker", "Kubernetes", "Git", "Linux",
                "微服务", "分布式", "机器学习", "AI", "算法"};
        for (String keyword : keywords) {
            if (text.toLowerCase().contains(keyword.toLowerCase())) {
                skills.add(keyword);
            }
        }
        return skills;
    }

    private List<String> extractExperience(String text) {
        List<String> experience = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.contains("公司") || line.contains("工作") || line.contains("担任")
                    || line.contains("负责") || line.contains("经验")) {
                experience.add(line.trim());
            }
        }
        return experience;
    }

    private int calculateOverallScore(String text) {
        int len = text.length();
        int score = 30;

        if (len > 200) {
            score += 10;
        }
        if (len > 500) {
            score += 10;
        }
        if (len > 1000) {
            score += 10;
        }

        if (text.contains("项目") && text.contains("负责")) {
            score += 10;
        }
        if (text.contains("技能") || text.contains("技术")) {
            score += 10;
        }
        if (text.contains("大学") || text.contains("学历")) {
            score += 10;
        }
        if (text.contains("公司") || text.contains("工作")) {
            score += 10;
        }

        return Math.min(score, 100);
    }

    private String generateSummary(int score) {
        if (score >= 80) {
            return "简历质量优秀，结构完整，内容充实，可以投递目标职位";
        }
        if (score >= 60) {
            return "简历质量良好，建议进一步优化关键词和项目描述";
        }
        return "简历需要较大改进，建议参考优秀简历模板进行重构";
    }

    private List<String> extractStrengths(String text) {
        List<String> strengths = new ArrayList<>();
        if (text.length() > 500) {
            strengths.add("简历内容充实，信息量充足");
        }
        if (text.contains("项目")) {
            strengths.add("包含项目经验描述");
        }
        if (text.contains("公司") || text.contains("工作")) {
            strengths.add("包含工作经历");
        }
        return strengths;
    }

    private List<String> extractWeaknesses(String text) {
        List<String> weaknesses = new ArrayList<>();
        if (text.length() < 200) {
            weaknesses.add("简历内容过短，建议补充更多细节");
        }
        if (!text.contains("项目")) {
            weaknesses.add("缺少项目经验描述");
        }
        if (!text.contains("技能") && !text.contains("技术")) {
            weaknesses.add("缺少技能列表，不利于ATS系统筛选");
        }
        return weaknesses;
    }

    private List<String> generateSuggestions(String text, List<String> weaknesses) {
        List<String> suggestions = new ArrayList<>();
        if (weaknesses.contains("简历内容过短，建议补充更多细节")) {
            suggestions.add("使用STAR法则描述项目经历（情境-任务-行动-结果）");
        }
        if (weaknesses.contains("缺少项目经验描述")) {
            suggestions.add("添加2-3个核心项目，重点突出个人贡献和技术亮点");
        }
        if (weaknesses.contains("缺少技能列表，不利于ATS系统筛选")) {
            suggestions.add("在简历中增加技能专长板块，列出核心技术栈");
        }
        suggestions.add("确保简历格式整洁，字体统一，便于HR快速浏览");
        suggestions.add("针对不同岗位定制简历，突出匹配的关键词");
        return suggestions;
    }
}
