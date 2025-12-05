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

import com.fasterxml.jackson.databind.JsonNode;
import fit.kltn_cookinote_backend.dtos.request.GeminiRequest;
import fit.kltn_cookinote_backend.dtos.response.GeminiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)) // Thử lại tối đa 3 lần, giãn cách 2s, 4s...
                            .filter(throwable ->
                                    // Chỉ thử lại nếu gặp lỗi 503 (Server Busy) hoặc 429 (Rate Limit)
                                    throwable instanceof WebClientResponseException.ServiceUnavailable ||
                                            throwable instanceof WebClientResponseException.TooManyRequests
                            )
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    // Nếu hết 3 lần vẫn lỗi thì ném lỗi ra ngoài như bình thường
                                    retrySignal.failure()
                            ))
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

    // Phương thức lấy phản hồi dạng Text (cho Chatbot)
    public String getGeneratedText(String prompt) {
        // Sử dụng fromTextPrompt thay vì fromPrompt
        GeminiRequest requestBody = GeminiRequest.fromTextPrompt(prompt);
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
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)) // Thử lại tối đa 3 lần, giãn cách 2s, 4s...
                            .filter(throwable ->
                                    // Chỉ thử lại nếu gặp lỗi 503 (Server Busy) hoặc 429 (Rate Limit)
                                    throwable instanceof WebClientResponseException.ServiceUnavailable ||
                                            throwable instanceof WebClientResponseException.TooManyRequests
                            )
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    // Nếu hết 3 lần vẫn lỗi thì ném lỗi ra ngoài như bình thường
                                    retrySignal.failure()
                            ))
                    .block();

            if (response == null || response.getFirstCandidateText() == null) {
                log.warn("Gemini API (chat) trả về null hoặc không có text.");
                throw new RuntimeException("AI không phản hồi, vui lòng thử lại.");
            }

            return response.getFirstCandidateText();

        } catch (Exception e) {
            log.error("Lỗi khi gọi Gemini API (chat): {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi kết nối với trợ lý AI: " + e.getMessage(), e);
        }
    }

    /**
     * Gọi API tạo Embedding từ văn bản.
     * Model sử dụng: text-embedding-004
     */
    public List<Double> getEmbedding(String text) {
        // Endpoint cho embedding
        String uri = "/v1beta/models/text-embedding-004:embedContent";

        try {
            // Cấu trúc request body theo document của Gemini
            // { "model": "...", "content": { "parts": [{ "text": "..." }] } }
            var requestBody = Map.of(
                    "model", "models/text-embedding-004",
                    "content", Map.of("parts", List.of(Map.of("text", text)))
            );

            // Gọi API
            JsonNode response = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path(uri).queryParam("key", apiKey).build())
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    // Retry nếu lỗi server (503) hoặc quá tải (429)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable ||
                                    throwable instanceof WebClientResponseException.TooManyRequests))
                    .block();

            // Parse kết quả: { "embedding": { "values": [ ... ] } }
            if (response != null && response.has("embedding") && response.get("embedding").has("values")) {
                List<Double> vector = new ArrayList<>();
                for (JsonNode val : response.get("embedding").get("values")) {
                    vector.add(val.asDouble());
                }
                return vector;
            }
            return List.of(); // Trả về rỗng nếu lỗi
        } catch (Exception e) {
            log.error("Lỗi lấy embedding từ Gemini: {}", e.getMessage());
            return List.of();
        }
    }
}