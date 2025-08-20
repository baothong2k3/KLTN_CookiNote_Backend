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

    /**
     * Cho phép 1 access token (theo jti) trong ttlSeconds.
     */
    public void allow(String jti, long ttlSeconds) {
        redis.opsForValue().set(key(jti), "1", Duration.ofSeconds(Math.max(1, ttlSeconds)));
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
}
