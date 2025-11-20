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

    @Override
    public GeneratedRecipeResponse enrichRecipe(GeneratedRecipeResponse rawData) {
        try {
            // Chuyển đổi dữ liệu thô sang JSON string để gửi cho AI
            String rawJson = objectMapper.writeValueAsString(rawData);
            String title = rawData.getTitle() != null ? rawData.getTitle() : "Món ăn";

            String prompt = buildEnrichmentPrompt(rawJson);

            log.info("Gửi yêu cầu đến AI để làm giàu dữ liệu cho: {}", title);
            return callAiAndParse(prompt, title);

        } catch (Exception e) {
            log.error("Lỗi khi enrich recipe: {}", e.getMessage());
            throw new RuntimeException("Không thể xử lý dữ liệu với AI. Vui lòng thử lại.");
        }
    }

    private GeneratedRecipeResponse callAiAndParse(String prompt, String contextInfo) {
        String jsonResponse = geminiApiClient.getGeneratedJson(prompt);
        try {
            GeneratedRecipeResponse response = objectMapper.readValue(jsonResponse, GeneratedRecipeResponse.class);

            // Đảm bảo các list không bị null
            if (response.getIngredients() == null) response.setIngredients(List.of());
            if (response.getSteps() == null) response.setSteps(List.of());
            if (response.getTitle() == null) response.setTitle(contextInfo);

            return response;
        } catch (Exception e) {
            log.error("Không thể deserialize JSON từ Gemini cho '{}'. Lỗi: {}. JSON: {}", contextInfo, e.getMessage(), jsonResponse);
            throw new RuntimeException("AI trả về dữ liệu không hợp lệ.");
        }
    }

    private String buildEnrichmentPrompt(String rawJsonData) {
        return String.format("""
                Bạn là biên tập viên ẩm thực. Tôi có dữ liệu công thức thô (JSON) nhưng thiếu thông tin (null).
                
                DỮ LIỆU ĐẦU VÀO:
                %s
                
                NHIỆM VỤ: Phân tích nội dung có sẵn để điền vào các trường null hoặc chỉnh sửa cho hợp lý.
                
                QUY TẮC QUAN TRỌNG:
                1. **Description**: Nếu null, viết mô tả ngắn (2-3 câu) hấp dẫn dựa trên Title.
                2. **Time (prepareTime, cookTime)**: Nếu null, hãy tổng hợp từ các bước bên dưới.
                3. **Ingredients**:
                   - Tách `quantity` nếu bị gộp trong `name`.
                   - Chuẩn hóa tên nguyên liệu.
                4. **Steps (QUAN TRỌNG)**:
                   - `content`: Giữ nguyên hoặc sửa lỗi chính tả.
                   - `suggestedTime`:
                        + Ưu tiên 1: Trích xuất số phút từ content (vd: "hầm 30 phút" -> 30).
                        + Ưu tiên 2: Nếu không có số cụ thể, hãy **ƯỚC LƯỢNG** thời gian hợp lý dựa trên hành động (ví dụ: "chiên gà chín vàng" -> khoảng 15, "sơ chế rau" -> khoảng 5, "trình bày" -> 1).
                        + Chỉ để null nếu bước đó không tốn thời gian đáng kể.
                   - `tips`: Trích xuất mẹo/lưu ý từ content. Nếu content quá ngắn, hãy tự bổ sung mẹo nhỏ phù hợp với bước đó (ví dụ bước chiên thì nhắc canh lửa).
                
                OUTPUT: Trả về duy nhất JSON chuẩn, không Markdown. Giữ nguyên cấu trúc JSON.
                """, rawJsonData);
    }
}
