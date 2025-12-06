/*
 * @ (#) ForkSuggestResponse.java    1.0    06/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 06/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.enums.Privacy;
import lombok.Builder;

import java.util.List;

/**
 * DTO phản hồi cho chức năng gợi ý Fork công thức.
 * Bao gồm dữ liệu do AI tạo ra (GeneratedRecipeResponse)
 * và thông tin ngữ cảnh từ công thức gốc (categoryId, privacy mặc định).
 */
@Builder
public record ForkSuggestResponse(
        Long categoryId, // Lấy từ công thức gốc
        String title,
        String description,
        Integer prepareTime,
        Integer cookTime,
        Difficulty difficulty,
        Privacy privacy, // Mặc định là PRIVATE
        List<GeneratedRecipeResponse.IngredientDto> ingredients,
        List<GeneratedRecipeResponse.StepDto> steps
) {
}