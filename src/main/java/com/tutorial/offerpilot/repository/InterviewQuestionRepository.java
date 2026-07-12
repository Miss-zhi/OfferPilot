/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.InterviewQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findBySessionIdOrderBySortOrder(String sessionId);

    List<InterviewQuestion> findByQuestionText(String questionText);

    void deleteBySessionId(String sessionId);

    /**
     * 按关键词模糊搜索面试题（不区分大小写）。
     * 替代 findAll() + 内存过滤，避免全表扫描。
     */
    @Query("SELECT q FROM InterviewQuestion q WHERE LOWER(q.questionText) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<InterviewQuestion> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 按关键词模糊搜索有答案的面试题（answerText 非空）。
     * 用于 searchAnswers 的 MySQL 检索路径。
     */
    @Query("SELECT q FROM InterviewQuestion q WHERE q.answerText IS NOT NULL AND q.answerText <> '' " +
           "AND LOWER(q.questionText) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<InterviewQuestion> searchWithAnswerByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
