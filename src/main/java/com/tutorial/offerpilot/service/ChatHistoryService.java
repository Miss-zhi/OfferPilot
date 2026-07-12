/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.dto.chat.MessageListItem;
import com.tutorial.offerpilot.dto.chat.MessageSaveRequest;
import com.tutorial.offerpilot.dto.chat.SearchResultItem;
import com.tutorial.offerpilot.dto.chat.SessionListItem;
import com.tutorial.offerpilot.entity.ChatMessage;
import com.tutorial.offerpilot.entity.ChatSession;
import com.tutorial.offerpilot.exception.BusinessException;
import com.tutorial.offerpilot.repository.ChatMessageRepository;
import com.tutorial.offerpilot.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;

    /** per-session 锁，确保同一 session 的 seq 生成原子性 */
    private final ConcurrentHashMap<String, Object> seqLocks = new ConcurrentHashMap<>();

    /** 会话列表 */
    public List<SessionListItem> listSessions(String userId) {
        return sessionRepo.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(s -> new SessionListItem(
                        s.getSessionId(),
                        s.getTitle(),
                        s.getActiveFunction(),
                        s.getMessageCount(),
                        s.getCreatedAt(),
                        s.getUpdatedAt()))
                .toList();
    }

    /** 创建新会话 */
    @Transactional
    public SessionListItem createSession(String userId, String activeFunction) {
        String sessionId = "sess-" + UUID.randomUUID().toString().substring(0, 8);
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setActiveFunction(activeFunction);
        session.setCreateBy(userId);
        sessionRepo.save(session);

        log.info("Created chat session: sessionId={}, userId={}", sessionId, userId);
        return new SessionListItem(
                sessionId, "", activeFunction, 0,
                session.getCreatedAt(), session.getUpdatedAt());
    }

    /** 删除会话及所有消息 */
    @Transactional
    public void deleteSession(String sessionId, String userId) {
        ChatSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此会话");
        }
        messageRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteBySessionId(sessionId);
        log.info("Deleted chat session: sessionId={}, userId={}", sessionId, userId);
    }

    /** 重命名会话 */
    @Transactional
    public void renameSession(String sessionId, String title, String userId) {
        ChatSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此会话");
        }
        session.setTitle(title);
        sessionRepo.save(session);
        log.info("Renamed chat session: sessionId={}, title={}", sessionId, title);
    }

    /** 获取会话全部消息（返回 DTO，不暴露 BaseEntity 敏感字段） */
    public List<MessageListItem> getMessages(String sessionId, String userId) {
        ChatSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此会话");
        }
        return messageRepo.findBySessionIdOrderBySeqAsc(sessionId)
                .stream()
                .map(m -> new MessageListItem(
                        m.getId(),
                        m.getSessionId(),
                        m.getRole(),
                        m.getContent(),
                        m.getThinkingContent(),
                        m.getToolCalls(),
                        m.getSeq(),
                        m.getCreatedAt()))
                .toList();
    }

    /** 保存一条消息（含 seq 生成、createBy、自动标题） */
    @Transactional
    public ChatMessage saveMessage(String sessionId, String userId, MessageSaveRequest request) {
        ChatSession session = sessionRepo.findBySessionId(sessionId)
                .orElseGet(() -> {
                    // 自动创建 session（兼容 /stream 中无 session 的情况）
                    ChatSession newSession = new ChatSession();
                    newSession.setSessionId(sessionId);
                    newSession.setUserId(userId);
                    newSession.setCreateBy(userId);
                    if (request.getActiveFunction() != null && !request.getActiveFunction().isEmpty()) {
                        newSession.setActiveFunction(request.getActiveFunction());
                    }
                    return sessionRepo.save(newSession);
                });

        // per-session 锁确保 seq 生成原子性
        Object lock = seqLocks.computeIfAbsent(sessionId, k -> new Object());
        int nextSeq;
        synchronized (lock) {
            Integer maxSeq = messageRepo.findMaxSeqBySessionId(sessionId);
            nextSeq = (maxSeq == null) ? 1 : maxSeq + 1;

            ChatMessage msg = new ChatMessage();
            msg.setSessionId(sessionId);
            msg.setRole(request.getRole());
            msg.setContent(request.getContent());
            msg.setThinkingContent(request.getThinkingContent());
            msg.setToolCalls(request.getToolCalls());
            msg.setSeq(nextSeq);
            msg.setCreateBy(userId);
            messageRepo.save(msg);

            // 更新会话统计
            session.setMessageCount(session.getMessageCount() + 1);

            // 首条用户消息 → 自动生成标题
            if (session.getTitle().isEmpty() && "USER".equals(request.getRole())) {
                String autoTitle = request.getContent().length() > 30
                        ? request.getContent().substring(0, 30)
                        : request.getContent();
                session.setTitle(autoTitle);
            }

            sessionRepo.save(session);

            log.info("Saved message: sessionId={}, role={}, seq={}", sessionId, request.getRole(), nextSeq);
            return msg;
        }
    }

    /** 全文搜索（FULLTEXT 优先，LIKE 兜底） */
    public List<SearchResultItem> searchMessages(String userId, String keyword) {
        // 获取用户所有会话
        List<ChatSession> sessions = sessionRepo.findByUserIdOrderByUpdatedAtDesc(userId);
        if (sessions.isEmpty()) return List.of();

        List<SearchResultItem> results = new ArrayList<>();
        for (ChatSession session : sessions) {
            // FULLTEXT 搜索
            List<ChatMessage> matches = messageRepo.searchByKeyword(
                    session.getSessionId(), keyword);
            // FULLTEXT 对短词/特殊字符可能无结果，降级 LIKE 搜索
            if (matches.isEmpty()) {
                matches = messageRepo.searchByKeywordLike(
                        session.getSessionId(), keyword);
            }
            if (!matches.isEmpty()) {
                String snippet = matches.get(0).getContent();
                if (snippet.length() > 80) {
                    snippet = snippet.substring(0, 80) + "...";
                }
                results.add(new SearchResultItem(
                        session.getSessionId(),
                        session.getTitle(),
                        snippet,
                        matches.size()));
            }
        }
        return results;
    }
}
