/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
package com.tutorial.offerpilot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redis;

    /** 对话限流：每分钟最多 30 次 */
    public boolean tryAcquireDialogue(String userId) {
        String key = "ratelimit:chat:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, 60, TimeUnit.SECONDS);
        }
        return count != null && count <= 30;
    }

    /** 上传限流：每分钟最多 10 次 */
    public boolean tryAcquireUpload(String userId) {
        String key = "ratelimit:upload:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, 60, TimeUnit.SECONDS);
        }
        return count != null && count <= 10;
    }
}
