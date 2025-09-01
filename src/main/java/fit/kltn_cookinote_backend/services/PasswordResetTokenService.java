/*
 * @ (#) PasswordResetTokenService.java    1.0    30/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/08/2025
 * @version: 1.0
 */

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {
    private final StringRedisTemplate redis;

    @Value("${app.password-reset.ttl-minutes:15}")
    private long ttlMinutes;

    private String key(String token) {
        return "pwreset:" + token;
    }

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(key(token), String.valueOf(userId), Duration.ofMinutes(ttlMinutes));
        return token;
    }

    public Optional<Long> peek(String token) {
        String v = redis.opsForValue().get(key(token));
        if (v == null) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(v));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Consume = get & delete (one-time)
     */
    public Optional<Long> consume(String token) {
        String k = key(token);
        String v = redis.opsForValue().get(k);
        if (v == null) return Optional.empty();
        redis.delete(k);
        try {
            return Optional.of(Long.parseLong(v));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttlMinutes * 60;
    }
}
