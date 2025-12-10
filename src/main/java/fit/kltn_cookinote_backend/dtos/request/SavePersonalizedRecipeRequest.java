/*
 * @ (#) SavePersonalizedRecipeRequest.java    1.0    10/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 10/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.enums.Privacy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SavePersonalizedRecipeRequest(
        @NotNull(message = "ID công thức gốc không được để trống")
        Long originalRecipeId,

        @NotBlank(message = "Tên món ăn không được để trống")
        @Size(max = 255)
        String title,

        @Size(max = 2048)
        String description,

        @PositiveOrZero
        Integer prepareTime,

        @PositiveOrZero
        Integer cookTime,

        @PositiveOrZero
        Integer calories,

        @PositiveOrZero
        Integer servings,

        Difficulty difficulty,

        // Có thể null, nếu null sẽ mặc định là PRIVATE
        Privacy privacy,

        // Có thể null, nếu null sẽ mặc định là 8
        Long categoryId,

        @NotEmpty(message = "Danh sách nguyên liệu không được rỗng")
        @Valid
        List<RecipeIngredientCreate> ingredients,

        @NotEmpty(message = "Danh sách bước nấu không được rỗng")
        @Valid
        List<RecipeStepCreate> steps
) {
}