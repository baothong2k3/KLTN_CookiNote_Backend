/*
 * @ (#) GeminiApiClient.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.GeminiRequest;
import fit.kltn_cookinote_backend.dtos.response.GeminiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GeminiApiClient {

    private final WebClient webClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String geminiModel;

    public GeminiApiClient(@Qualifier("geminiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Gửi một prompt đến Gemini và nhận về nội dung JSON text thô.
     * (Đã được cấu hình để yêu cầu JSON trong GeminiRequest.fromPrompt)
     *
     * @param prompt Prompt chi tiết yêu cầu AI.
     * @return Chuỗi JSON thô do AI tạo ra.
     * @throws RuntimeException nếu AI trả về lỗi hoặc null.
     */
    public String getGeneratedJson(String prompt) {
        GeminiRequest requestBody = GeminiRequest.fromPrompt(prompt);
        String uri = String.format("/v1beta/models/%s:generateContent", geminiModel);

        try {
            GeminiResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(uri)
                            .queryParam("key", apiKey)
                            .build())
                    .body(Mono.just(requestBody), GeminiRequest.class)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block(); // Chờ kết quả

            if (response == null || response.getFirstCandidateText() == null) {
                log.warn("Gemini API (generate) trả về null hoặc không có text.");
                throw new RuntimeException("AI không thể tạo công thức, vui lòng thử lại.");
            }

            // Trả về chuỗi JSON thô từ AI
            return response.getFirstCandidateText();

        } catch (Exception e) {
            log.error("Lỗi khi gọi Gemini API (generate): {}", e.getMessage(), e);
            // Ném lỗi runtime để GlobalExceptionHandler bắt
            throw new RuntimeException("Lỗi khi kết nối với AI: " + e.getMessage(), e);
        }
    }
}