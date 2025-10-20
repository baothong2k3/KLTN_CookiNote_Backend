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

    private String userSetKey(Long userId) {
        return "rftu:" + userId;
    }

    private String key(String token) {
        return "rft:" + token;
    }

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        String k = key(token);
        // value: userId
        redis.opsForValue().set(k, String.valueOf(userId), Duration.ofDays(refreshTtlDays));
        // lưu danh sách refresh theo user để revoke all
        redis.opsForSet().add(userSetKey(userId), token);
        redis.expire(userSetKey(userId), Duration.ofDays(refreshTtlDays));

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
        revoke(oldToken);
        return issue(userId);
    }

    public void revoke(String token) {
        String k = key(token);
        String userId = redis.opsForValue().get(k);
        if (userId != null) {
            redis.opsForSet().remove(userSetKey(Long.parseLong(userId)), token);
        }
        redis.delete(k);
    }

    /**
     * Revoke tất cả refresh token của 1 user.
     */
    public void revokeAllForUser(Long userId) {
        String ukey = userSetKey(userId);
        var tokens = redis.opsForSet().members(ukey);
        if (tokens != null && !tokens.isEmpty()) {
            var keys = tokens.stream().map(this::key).toList();
            redis.delete(keys);
        }
        redis.delete(ukey);
    }

    public long refreshTtlSeconds() {
        return Duration.ofDays(refreshTtlDays).toSeconds();
    }
}
