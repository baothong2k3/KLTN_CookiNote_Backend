/*
 * @ (#) LoginAttemptRateLimiter.java    1.0    16/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 16/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.limiters.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LoginAttemptRateLimiter {

    private final StringRedisTemplate redis;

    private static final int MAX_ATTEMPTS = 5; // Tối đa 5 lần sai
    // Thời gian chặn (ví dụ: 15 phút)
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    // Thời gian nhớ các lần đếm (ví dụ: 10 phút)
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(10);

    // Key đếm số lần sai
    private String getFailKey(String username) {
        return "login:fail:" + username.toLowerCase();
    }

    // Key chặn
    private String getBlockKey(String username) {
        return "login:block:" + username.toLowerCase();
    }

    /**
     * Ghi lại một lần đăng nhập sai.
     * Nếu vượt quá 5 lần, sẽ tạo khóa block.
     */
    public void recordFailedLogin(String username) {
        String failKey = getFailKey(username);
        String blockKey = getBlockKey(username);

        // Tăng số lần đếm
        Long attempts = redis.opsForValue().increment(failKey);

        if (attempts == null) return;

        if (attempts == 1) {
            // Nếu là lần sai đầu tiên, đặt thời gian hết hạn cho key đếm
            redis.expire(failKey, ATTEMPT_WINDOW);
        }

        if (attempts > MAX_ATTEMPTS) {
            // Vượt quá 5 lần -> Chặn
            redis.opsForValue().set(blockKey, "1", BLOCK_DURATION);
            // (Tùy chọn) Xóa key đếm đi vì đã bị chặn
            redis.delete(failKey);
        }
    }

    /**
     * Ghi lại một lần đăng nhập thành công.
     * Xóa hết các theo dõi.
     */
    public void recordSuccessfulLogin(String username) {
        redis.delete(getFailKey(username));
        redis.delete(getBlockKey(username));
    }

    /**
     * Kiểm tra xem username có đang bị chặn không.
     * Nếu có, ném TooManyRequestsException.
     */
    public void checkAndThrowIfBlocked(String username) {
        String blockKey = getBlockKey(username);
        String blocked = redis.opsForValue().get(blockKey);

        if (blocked != null) {
            // Lấy thời gian còn lại của khóa block
            Long retryAfterSeconds = redis.getExpire(blockKey, TimeUnit.SECONDS);
            if (retryAfterSeconds == null || retryAfterSeconds <= 0) {
                retryAfterSeconds = BLOCK_DURATION.toSeconds();
            }

            throw new TooManyRequestsException(
                    "Bạn đã đăng nhập sai quá 5 lần. Vui lòng thử lại sau " + retryAfterSeconds + " giây.",
                    retryAfterSeconds
            );
        }
    }
}
