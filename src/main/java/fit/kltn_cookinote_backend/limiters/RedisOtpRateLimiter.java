/*
 * @ (#) RedisOtpRateLimiter.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.limiters;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RateWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisOtpRateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrWithTtlScript;

    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private String key(long userId, String purpose) {
        String hour = LocalDateTime.now(ZONE).format(HOUR_FMT);
        return "otp:quota:%d:%s:%s".formatted(userId, purpose, hour);
    }

    /**
     * Tăng đếm và ném 429 nếu vượt quá giới hạn.
     */
    public void consumeOrThrow(long userId, String purpose) {
        String key = key(userId, purpose);

        // INCR + (nếu lần đầu) PEXPIRE(windowMs) — atomic
        Long used = redis.execute(
                incrWithTtlScript,
                Collections.singletonList(key),
                String.valueOf(WINDOW.toMillis())
        );
        long usedCount = used;

        if (usedCount > LIMIT) {
            // Lấy TTL còn lại (ưu tiên mili giây để chính xác, rồi quy về giây)
            long ttlMs = redis.getExpire(key, TimeUnit.MILLISECONDS);
            long retryAfterSeconds;

            if (ttlMs < 0) {
                // -2: key không tồn tại; -1: không có TTL — fallback về cửa sổ gốc
                retryAfterSeconds = WINDOW.toSeconds();
            } else {
                retryAfterSeconds = Math.max(1, ttlMs / 1000);
            }

            throw new TooManyRequestsException(
                    "Bạn đã vượt quá số lần gửi OTP cho phép trong 1 giờ. Vui lòng thử lại sau "
                            + retryAfterSeconds + " giây.",
                    retryAfterSeconds
            );
        }
    }

    /**
     * Xem đã dùng bao nhiêu lần trong giờ hiện tại.
     */
    public RateWindow currentWindow(long userId, String purpose) {
        String k = key(userId, purpose);
        // đã dùng
        String v = redis.opsForValue().get(k);
        long used = (v == null) ? 0 : Long.parseLong(v);
        // ttl còn lại
        long ttlMs = redis.getExpire(k, TimeUnit.MILLISECONDS);
        long ttlSeconds = (ttlMs < 0) ? WINDOW.toSeconds() : Math.max(0, ttlMs / 1000);
        return new RateWindow(used, LIMIT, ttlSeconds);
    }
}
