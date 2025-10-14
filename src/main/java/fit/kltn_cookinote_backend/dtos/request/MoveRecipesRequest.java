/*
 * @ (#) MoveRecipesRequest.java    1.0    14/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 14/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MoveRecipesRequest(
        @NotNull(message = "ID danh mục nguồn không được để trống")
        Long sourceCategoryId,

        @NotNull(message = "ID danh mục đích không được để trống")
        Long destinationCategoryId,

        // Nếu null hoặc rỗng, tất cả công thức sẽ được chuyển
        List<Long> recipeIds
) {
}
