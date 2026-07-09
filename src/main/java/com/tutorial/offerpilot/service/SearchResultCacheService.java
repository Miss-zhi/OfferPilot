/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchResultCacheService {

    private final StringRedisTemplate redis;

    private static final int SEARCH_CACHE_TTL_MINUTES = 10;
    private static final int PROFILE_CACHE_TTL_MINUTES = 5;

    public void cacheSearchResult(String kbId, String query, String resultJson) {
        String key = "search:" + kbId + ":" + DigestUtils.md5DigestAsHex(query.getBytes());
        redis.opsForValue().set(key, resultJson, SEARCH_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    public Optional<String> getCachedSearchResult(String kbId, String query) {
        String key = "search:" + kbId + ":" + DigestUtils.md5DigestAsHex(query.getBytes());
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    public void cacheUserProfile(String userId, String profileJson) {
        redis.opsForValue().set("profile:" + userId, profileJson, PROFILE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    public Optional<String> getCachedUserProfile(String userId) {
        return Optional.ofNullable(redis.opsForValue().get("profile:" + userId));
    }
}
