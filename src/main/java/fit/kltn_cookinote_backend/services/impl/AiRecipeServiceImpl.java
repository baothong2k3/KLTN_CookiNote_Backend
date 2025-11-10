/*
 * @ (#) AiRecipeServiceImpl.java    1.0    08/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 08/11/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.dtos.request.GenerateRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.GeneratedRecipeResponse;
import fit.kltn_cookinote_backend.services.AiRecipeService;
import fit.kltn_cookinote_backend.services.GeminiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiRecipeServiceImpl implements AiRecipeService {

    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public GeneratedRecipeResponse generateRecipe(GenerateRecipeRequest request) {
        String dishNameQuery = request.dishName();

        // 1. Xây dựng prompt chi tiết
        String prompt = buildGenerationPrompt(dishNameQuery);

        log.info("Gửi yêu cầu đến AI để tạo công thức cho: {}", dishNameQuery);

        // 2. Gọi API Client
        String jsonResponse = geminiApiClient.getGeneratedJson(prompt);
        log.debug("Nhận JSON thô từ AI: {}", jsonResponse);

        try {
            // 3. Deserialize JSON
            GeneratedRecipeResponse response = objectMapper.readValue(jsonResponse, GeneratedRecipeResponse.class);

            // 4. Hậu xử lý

            // CHỈ giữ lại logic đảm bảo list không null
            if (response.getIngredients() == null) {
                response.setIngredients(List.of());
            }
            if (response.getSteps() == null) {
                response.setSteps(List.of());
            }
            // Nếu AI không trả về title, chúng ta sẽ để nó là dishNameQuery ban đầu
            if (response.getTitle() == null || response.getTitle().isBlank()) {
                response.setTitle(dishNameQuery);
            }

            log.info("Tạo và parse công thức từ AI thành công cho: {}", dishNameQuery);
            return response;

        } catch (Exception e) { // Bắt Exception chung (bao gồm IOException)
            log.error("Không thể deserialize JSON từ Gemini cho món '{}'. Lỗi: {}. JSON: {}", dishNameQuery, e.getMessage(), jsonResponse);
            throw new RuntimeException("AI đã trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
        }
    }

    /**
     * Xây dựng prompt để yêu cầu Gemini trả về JSON theo đúng định dạng.
     * (ĐÃ CẬP NHẬT)
     */
    private String buildGenerationPrompt(String userInput) {

        // Prompt này yêu cầu AI thực hiện 2 nhiệm vụ:
        // 1. Tự xác định tên món ăn (ví dụ: "Trứng chiên")
        // 2. Dùng tên đó để điền vào trường "title" trong JSON
        return String.format("""
                Bạn là một chuyên gia ẩm thực hàng đầu.
                Nhiệm vụ 1: Hãy xác định **TÊN MÓN ĂN** (ví dụ: 'Trứng chiên', 'Gà kho gừng') từ yêu cầu của người dùng.
                YÊU CẦU CỦA NGƯỜI DÙNG: "%s"
                
                Nhiệm vụ 2: Bây giờ, hãy tạo công thức nấu ăn chi tiết cho món ăn bạn vừa xác định ở Nhiệm vụ 1.
                
                YÊU CẦU OUTPUT:
                Chỉ trả về duy nhất một đối tượng JSON. TUYỆT ĐỐI KHÔNG SỬ DỤNG MARKDOWN (không dùng ```json ... ```).
                JSON phải tuân theo cấu trúc sau (Sử dụng Tiếng Việt có dấu):
                {
                  "title": "<Tên món ăn chính BẠN ĐÃ XÁC ĐỊNH ở Nhiệm vụ 1 (ví dụ: 'Trứng chiên')>",
                  "description": "<Mô tả ngắn gọn, hấp dẫn về món ăn (1-2 câu)>",
                  "prepareTime": <Số phút chuẩn bị (ví dụ: 15)>,
                  "cookTime": <Số phút nấu (ví dụ: 30)>,
                  "difficulty": "<EASY, MEDIUM, hoặc HARD>",
                  "ingredients": [
                    { "name": "<Tên nguyên liệu 1>", "quantity": "<Số lượng và đơn vị (ví dụ: '100g', '1 muỗng canh')>" },
                    { "name": "<Tên nguyên liệu 2>", "quantity": "<Số lượng và đơn vị>" }
                  ],
                  "steps": [
                    { "stepNo": 1, "content": "<Nội dung chi tiết của bước 1>", "suggestedTime": <Số phút gợi ý cho bước này (Integer, có thể null)>, "tips": "<Mẹo hoặc lưu ý cho bước này (String, có thể null)>" },
                    { "stepNo": 2, "content": "<Nội dung chi tiết của bước 2>", "suggestedTime": null, "tips": null }
                  ]
                }
                """, userInput);
    }
}
