/*
 * @ (#) ShoppingListCategoryResponse.java    1.0    19/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 19/12/2025
 * @version: 1.0
 */

import lombok.Builder;
import java.util.List;

@Builder
public record ShoppingListCategoryResponse(
        String categoryName,   // Tên danh mục (VD: "Thịt & Gia cầm")
        String categoryIcon,   // Icon đại diện (nếu có)
        List<CategoryItem> items
) {
    @Builder
    public record CategoryItem(
            Long id,              // ID của ShoppingList item
            String ingredientName,
            String quantity,
            Boolean isChecked,
            Long originalRecipeId // Để FE có thể link về công thức gốc nếu cần
    ) {}
}