/*
 * @ (#) PendingEmailChangeStore.java    1.0    24/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 24/08/2025
 * @version: 1.0
 */

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class PendingEmailChangeStore {
    private final StringRedisTemplate redis;

    private String key(long userId) {
        return "emailchg:" + userId;
    }

    public void put(long userId, String newEmail, Duration ttl) {
        redis.opsForValue().set(key(userId), newEmail, ttl);
    }

    public String get(long userId) {
        return redis.opsForValue().get(key(userId));
    }

    public void delete(long userId) {
        redis.delete(key(userId));
    }
}
