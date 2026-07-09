/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import com.tutorial.offerpilot.repository.TokenBlacklistRepository;
import com.tutorial.offerpilot.entity.TokenBlacklist;
import com.tutorial.offerpilot.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository blacklistRepo;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void blacklistToken(String token, String userId, String reason) {
        TokenBlacklist entry = new TokenBlacklist();
        entry.setTokenJti(jwtTokenProvider.getJtiFromToken(token));
        entry.setUserId(userId);
        entry.setExpireAt(Instant.ofEpochMilli(jwtTokenProvider.getExpirationFromToken(token)));
        entry.setBlacklistedAt(Instant.now());
        entry.setReason(reason);
        blacklistRepo.save(entry);
        log.info("Token blacklisted: userId={}, reason={}", userId, reason);
    }

    public boolean isBlacklisted(String token) {
        String jti = jwtTokenProvider.getJtiFromToken(token);
        return blacklistRepo.existsByTokenJti(jti);
    }

    /** 每小时清理过期的黑名单记录 */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanExpiredTokens() {
        blacklistRepo.deleteByExpireAtBefore(Instant.now());
        log.info("Cleaned expired token blacklist entries");
    }
}
