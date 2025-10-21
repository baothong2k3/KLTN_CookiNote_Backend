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

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.dtos.request.GeminiRequest;
import fit.kltn_cookinote_backend.dtos.response.GeminiResponse;
import fit.kltn_cookinote_backend.dtos.response.AiScoreResponse;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j // Bỏ @RequiredArgsConstructor
public class GeminiApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String fullApiUrl;

    public GeminiApiClient(@Qualifier("geminiWebClient") WebClient webClient,
                           ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Gọi AI để chấm điểm sự phù hợp của 1 công thức.
     * Đây là một lời gọi blocking (đồng bộ) để dễ tích hợp.
     */
    public AiScoreResponse getSuggestionScore(List<String> shoppingList, Recipe candidate) {
        String prompt = buildPrompt(shoppingList, candidate);
        GeminiRequest requestBody = GeminiRequest.fromPrompt(prompt);

        String uri = fullApiUrl.substring(fullApiUrl.indexOf("/v1beta"));

        try {
            GeminiResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(uri)
                            .queryParam("key", apiKey)
                            .build())
                    .body(Mono.just(requestBody), GeminiRequest.class)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block();

            if (response == null || response.getFirstCandidateText() == null) {
                log.warn("Gemini API trả về null hoặc không có text. Recipe ID: {}", candidate.getId());
                return createFallbackScore();
            }

            String jsonText = response.getFirstCandidateText();
            return objectMapper.readValue(jsonText, AiScoreResponse.class);

        } catch (Exception e) {
            log.error("Lỗi khi gọi Gemini API cho Recipe ID: {}. Lỗi: {}", candidate.getId(), e.getMessage());
            return createFallbackScore();
        }
    }

    private AiScoreResponse createFallbackScore() {
        AiScoreResponse fallback = new AiScoreResponse();
        fallback.setMainIngredientMatchScore(0.0);
        fallback.setOverallMatchScore(0.0);
        fallback.setJustification("Lỗi khi xử lý gợi ý.");
        return fallback;
    }

    /**
     * Tạo ra câu lệnh (prompt) rõ ràng để AI hiểu nhiệm vụ.
     */
    private String buildPrompt(List<String> shoppingList, Recipe candidate) {
        List<String> recipeIngredients = candidate.getIngredients().stream()
                .map(RecipeIngredient::getName)
                .toList();

        String shoppingListStr = shoppingList.stream().map(String::trim).toList().toString();
        String recipeIngredientsStr = recipeIngredients.stream().map(String::trim).toList().toString();

        return String.format(
                """
                        Bạn là một trợ lý đầu bếp chuyên nghiệp. Nhiệm vụ của bạn là chấm điểm sự phù hợp của một công thức (candidateRecipe) dựa trên danh sách nguyên liệu có sẵn (shoppingList).
                        
                        Hãy thực hiện các bước sau:
                        1. Phân tích 'shoppingList' để xác định đâu là **nguyên liệu chính** (ví dụ: thịt, cá, rau củ chính) và đâu là **nguyên liệu phụ** (gia vị, hành, tỏi, nước mắm).
                        2. So sánh các nguyên liệu chính này với 'candidateRecipeIngredients'.
                        3. Tính toán 2 loại điểm (thang 10):
                           - 'mainIngredientMatchScore': Điểm này đánh giá mức độ công thức sử dụng (khớp) các **nguyên liệu chính** từ shoppingList. Đây là điểm quan trọng nhất.
                           - 'overallMatchScore': Điểm này đánh giá tổng %% nguyên liệu của công thức có trong shoppingList (bao gồm cả chính và phụ).
                        4. Cung cấp một 'justification' (1 câu) giải thích ngắn gọn lý do bạn chấm điểm như vậy (bằng Tiếng Việt).
                        
                        INPUT:
                        - shoppingList: %s
                        - candidateRecipeTitle: %s
                        - candidateRecipeIngredients: %s
                        
                        YÊU CẦU OUTPUT:
                        Chỉ trả về duy nhất một đối tượng JSON, không giải thích gì thêm, không dùng markdown.
                        
                        JSON FORMAT:
                        {
                          "mainIngredientMatchScore": <double>,
                          "overallMatchScore": <double>,
                          "justification": "<string>"
                        }
                        """,
                shoppingListStr,
                candidate.getTitle(),
                recipeIngredientsStr
        );
    }
}
