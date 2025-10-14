/*
 * @ (#) ForkRecipeRequest.java    1.0    14/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 14/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.enums.Privacy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

// DTO này cho phép người dùng tùy chỉnh công thức khi fork
public record ForkRecipeRequest(
        @NotNull(message = "categoryId bắt buộc")
        Long categoryId,

        @NotBlank @Size(max = 255)
        String title,

        @Size(max = 2048)
        String description,

        @PositiveOrZero
        Integer prepareTime,

        @PositiveOrZero
        Integer cookTime,

        Difficulty difficulty,

        // Bắt buộc người dùng phải chọn lại chế độ riêng tư cho công thức của họ
        @NotNull(message = "Chế độ riêng tư không được để trống")
        Privacy privacy,

        @NotEmpty(message = "Danh sách nguyên liệu không được rỗng")
        @Valid
        List<RecipeIngredientCreate> ingredients,

        @NotEmpty(message = "Danh sách bước nấu không được rỗng")
        @Valid
        List<RecipeStepCreate> steps
) {
}
