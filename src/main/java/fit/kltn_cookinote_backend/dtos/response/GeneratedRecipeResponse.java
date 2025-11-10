/*
 * @ (#) GeneratedRecipeResponse.java    1.0    08/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 08/11/2025
 * @version: 1.0
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fit.kltn_cookinote_backend.enums.Difficulty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO này hứng dữ liệu JSON do AI tạo ra.
 * Cấu trúc của nó tương tự RecipeCreateRequest nhưng không có validation
 * để tránh lỗi khi deserialize.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Bỏ qua nếu AI thêm trường lạ
public class GeneratedRecipeResponse {

    private String title;
    private String description;
    private Integer prepareTime;
    private Integer cookTime;
    private Difficulty difficulty;
    private List<IngredientDto> ingredients;
    private List<StepDto> steps;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IngredientDto {
        private String name;
        private String quantity;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StepDto {
        private Integer stepNo;
        private String content;
        private Integer suggestedTime;
        private String tips;
    }
}
