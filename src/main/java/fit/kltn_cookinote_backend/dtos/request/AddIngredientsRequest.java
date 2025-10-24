/*
 * @ (#) AddIngredientsRequest.java    1.0    24/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 24/10/2025
 * @version: 1.0
 */

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO chứa danh sách các nguyên liệu cần thêm vào một Recipe.
 */
public record AddIngredientsRequest(
        @NotEmpty(message = "Danh sách nguyên liệu không được rỗng.")
        @Valid // Đảm bảo các RecipeIngredientCreate bên trong cũng được validate
        List<RecipeIngredientCreate> ingredients
) {
}
