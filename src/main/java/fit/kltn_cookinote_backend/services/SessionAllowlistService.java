/*
 * @ (#) SessionAllowlistService.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SessionAllowlistService {
    private final StringRedisTemplate redis;

    private String key(String jti) {
        return "session:" + jti;
    }

    private String userSetKey(Long userId) {
        return "sessionu:" + userId;
    }

    /**
     * Cho phép 1 access token (theo jti) trong ttlSeconds.
     */
    public void allow(Long userId, String jti, long ttlSeconds) {
        long ttl = Math.max(1, ttlSeconds);
        redis.opsForValue().set(key(jti), "1", Duration.ofSeconds(ttl));
        // lưu danh sách jti theo user để revoke all
        redis.opsForSet().add(userSetKey(userId), jti);
        redis.expire(userSetKey(userId), Duration.ofSeconds(ttl));
    }

    /**
     * Kiểm tra jti có trong allowlist không.
     */
    public boolean isAllowed(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(jti)));
    }

    /**
     * Huỷ hiệu lực ngay lập tức.
     */
    public void revoke(String jti) {
        redis.delete(key(jti));
    }

    /**
     * Revoke tất cả access token (jtis) của 1 user.
     */
    public void revokeAllForUser(Long userId) {
        var jtis = redis.opsForSet().members(userSetKey(userId));
        if (jtis != null && !jtis.isEmpty()) {
            var keys = jtis.stream().map(this::key).toList();
            redis.delete(keys);
        }
        redis.delete(userSetKey(userId));
    }
}
