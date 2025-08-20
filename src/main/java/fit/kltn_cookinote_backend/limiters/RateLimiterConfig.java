/*
 * @ (#) RateLimiterConfig.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.limiters;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RateLimiterConfig {
    @Bean
    public DefaultRedisScript<Long> incrWithTtlScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                // KEYS[1]=key, ARGV[1]=windowMs
                "local current = redis.call('INCR', KEYS[1]); " +
                        "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end " +
                        "return current;"
        );
        return script;
    }
}
