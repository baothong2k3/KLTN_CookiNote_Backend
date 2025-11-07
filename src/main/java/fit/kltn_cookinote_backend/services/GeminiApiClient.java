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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.dtos.request.GeminiRequest;
import fit.kltn_cookinote_backend.dtos.response.GeminiResponse;
import fit.kltn_cookinote_backend.dtos.response.AiScoreResponse;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import fit.kltn_cookinote_backend.utils.ShoppingListUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GeminiApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String geminiModel;

    public GeminiApiClient(@Qualifier("geminiWebClient") WebClient webClient,
                           ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Gọi AI để chấm điểm một lô công thức.
     */
    public List<AiScoreResponse> getSuggestionScoresBatch(List<String> shoppingList, List<Recipe> candidates) {
        String prompt = buildBatchPrompt(shoppingList, candidates);
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
                    .block();

            if (response == null || response.getFirstCandidateText() == null) {
                log.warn("Gemini API (batch) trả về null hoặc không có text. Số lượng: {}", candidates.size());
                return createFallbackScores(candidates); // Sửa: Trả về danh sách lỗi CÓ ID
            }

            String jsonText = response.getFirstCandidateText();

            // Sửa Warning: Thay thế TypeReference tường minh bằng diamond operator <>
            return objectMapper.readValue(jsonText, new TypeReference<>() {
            });

        } catch (Exception e) {
            log.error("Lỗi khi gọi Gemini API (batch). Lỗi: {}", e.getMessage());
            return createFallbackScores(candidates); // Sửa: Trả về danh sách lỗi CÓ ID
        }
    }

    /**
     * Tạo prompt cho việc chấm điểm hàng loạt.
     */
    private String buildBatchPrompt(List<String> shoppingList, List<Recipe> candidates) {
        String shoppingListStr = shoppingList.stream()
                .map(ShoppingListUtils::normalize) // Chuẩn hóa
                .filter(s -> !s.isEmpty())
                .toList().toString();

        // Chuyển danh sách recipe thành một chuỗi JSON đơn giản cho prompt
        String candidatesStr = candidates.stream()
                .map(recipe -> String.format(
                        "{\"id\": %d, \"title\": \"%s\", \"ingredients\": %s}",
                        recipe.getId(),
                        recipe.getTitle(),
                        recipe.getIngredients().stream()
                                .map(RecipeIngredient::getName)
                                .map(ShoppingListUtils::normalize) // Chuẩn hóa
                                .filter(s -> !s.isEmpty())
                                .toList()
                ))
                .toList().toString();

        return String.format("""
                        Bạn là một trợ lý đầu bếp chuyên nghiệp. Nhiệm vụ của bạn là chấm điểm sự phù hợp của MỘT DANH SÁCH các công thức (candidateRecipes) dựa trên danh sách nguyên liệu có sẵn (shoppingList).
                        
                        Hãy thực hiện các bước sau cho TỪNG công thức trong 'candidateRecipes':
                        1. Phân tích 'shoppingList' để xác định **nguyên liệu chính** (thịt, cá, rau củ chính) và **nguyên liệu phụ** (gia vị, hành, tỏi).
                        2. So sánh các nguyên liệu chính này với 'ingredients' của công thức.
                        3. Tính toán 2 loại điểm (thang 10):
                           - 'mainIngredientMatchScore': Mức độ công thức sử dụng (khớp) các **nguyên liệu chính** từ shoppingList. Đây là điểm quan trọng nhất.
                           - 'overallMatchScore': Tổng %% nguyên liệu của công thức có trong shoppingList (cả chính và phụ).
                        4. Cung cấp một 'justification' (1 câu) giải thích ngắn gọn lý do (bằng Tiếng Việt).
                        
                        INPUT:%n- shoppingList: %s%n- candidateRecipes: %s
                        
                        YÊU CẦU OUTPUT:
                        Chỉ trả về duy nhất một JSON ARRAY, không giải thích gì thêm, không dùng markdown.
                        Mỗi object trong array PHẢI chứa "id" (lấy từ 'id' của candidateRecipes input), và các điểm số.
                        
                        JSON ARRAY FORMAT:
                        [
                          { "id": <long>, "mainIngredientMatchScore": <double>, "overallMatchScore": <double>, "justification": "<string>" },
                          { "id": <long>, "mainIngredientMatchScore": <double>, "overallMatchScore": <double>, "justification": "<string>" }
                        ]
                        """,
                shoppingListStr, candidatesStr
        );

    }

    /**
     * HELPER MỚI: Trả về danh sách lỗi nếu batch-call thất bại
     */
    private List<AiScoreResponse> createFallbackScores(List<Recipe> candidates) {
        return candidates.stream()
                .map(recipe -> createFallbackScore(recipe.getId()))
                .toList();
    }

    public AiScoreResponse createFallbackScore(Long recipeId) {
        AiScoreResponse fallback = new AiScoreResponse();
        fallback.setId(recipeId != null ? recipeId : -1L);
        fallback.setMainIngredientMatchScore(0.0);
        fallback.setOverallMatchScore(0.0);
        fallback.setJustification("Lỗi khi xử lý gợi ý.");
        return fallback;
    }
}