/*
 * @ (#) RefreshTokenService.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final StringRedisTemplate redis;

    @Value("${app.jwt.refresh-ttl-days}")
    private long refreshTtlDays;

    private String key(String token) {
        return "rft:" + token;
    }

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        String k = key(token);
        // value: userId
        redis.opsForValue().set(k, String.valueOf(userId), Duration.ofDays(refreshTtlDays));
        return token;
    }

    public Optional<Long> validate(String token) {
        String userId = redis.opsForValue().get(key(token));
        return userId == null ? Optional.empty() : Optional.of(Long.parseLong(userId));
    }

    /**
     * Rotation: xoá token cũ, phát hành token mới.
     */
    public String rotate(String oldToken, Long userId) {
        redis.delete(key(oldToken));
        return issue(userId);
    }

    public void revoke(String token) {
        redis.delete(key(token));
    }

    public long refreshTtlSeconds() {
        return Duration.ofDays(refreshTtlDays).toSeconds();
    }
}
