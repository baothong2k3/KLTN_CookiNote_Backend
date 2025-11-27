/*
 * @ (#) ActuatorConfig.java    1.0    27/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.configs;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/11/2025
 * @version: 1.0
 */

import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorConfig {
    @Bean
    public HttpExchangeRepository httpExchangeRepository() {
        // Lưu trữ 100 request gần nhất trong RAM để Admin xem
        return new InMemoryHttpExchangeRepository();
    }
}
