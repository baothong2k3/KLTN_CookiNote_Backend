/*
 * @ (#) ApiClientConfig.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.configs;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ApiClientConfig {

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    /**
     * Tạo một Bean WebClient với tên "geminiWebClient".
     * Bean này được cấu hình sẵn base URL và header mặc định.
     */
    @Bean("geminiWebClient")
    public WebClient geminiWebClient() {
        // Lấy URL cơ sở (ví dụ: https://generativelanguage.googleapis.com)
        String baseUrl = geminiApiUrl.substring(0, geminiApiUrl.indexOf("/v1beta"));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
