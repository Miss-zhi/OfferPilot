/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.repository;

import com.tutorial.offerpilot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderBySeqAsc(String sessionId);

    @Modifying
    @Transactional
    void deleteBySessionId(String sessionId);

    @Query(value = "SELECT MAX(m.seq) FROM ChatMessage m WHERE m.sessionId = :sessionId")
    Integer findMaxSeqBySessionId(@Param("sessionId") String sessionId);

    @Query(value = "SELECT * FROM op_chat_message m WHERE m.session_id = :sessionId " +
            "AND MATCH(m.content, m.thinking_content) AGAINST(:keyword IN BOOLEAN MODE) " +
            "ORDER BY m.seq ASC", nativeQuery = true)
    List<ChatMessage> searchByKeyword(@Param("sessionId") String sessionId,
                                       @Param("keyword") String keyword);

    /** LIKE 兜底搜索 — FULLTEXT 对短词/特殊字符无效时降级使用 */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId " +
            "AND (LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(m.thinkingContent) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY m.seq ASC")
    List<ChatMessage> searchByKeywordLike(@Param("sessionId") String sessionId,
                                           @Param("keyword") String keyword);
}
