/*
 * @ (#) DeleteIngredientsRequest.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO chứa danh sách ID của các nguyên liệu (RecipeIngredient) cần xóa khỏi Recipe.
 */
public record DeleteIngredientsRequest(
        @NotEmpty(message = "Danh sách ID nguyên liệu không được rỗng.")
        List<Long> ingredientIds
) {
}
