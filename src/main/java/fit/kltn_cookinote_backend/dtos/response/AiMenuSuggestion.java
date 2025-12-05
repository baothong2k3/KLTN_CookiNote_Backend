/*
 * @ (#) AiMenuSuggestion.java    1.0    05/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 05/12/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiMenuSuggestion {
    private Long originalRecipeId; // ID tham chiếu đến món gốc trong DB
    private String title;          // Tên món (có thể AI gợi ý sửa nhẹ)
    private String description;    // Lý do tại sao món này phù hợp
    private Integer calories;      // Số calo đã tính toán lại
    private Integer servings;      // Khẩu phần user yêu cầu
    private List<IngredientDto> ingredients; // Nguyên liệu đã điều chỉnh định lượng

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IngredientDto {
        private String name;
        private String quantity;
    }
}
