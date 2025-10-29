/*
 * @ (#) GroupedShoppingListResponse.java    1.0    12/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 12/10/2025
 * @version: 1.0
 */

import lombok.Builder;

import java.util.List;

@Builder
public record GroupedShoppingListResponse(
        Long recipeId,
        String recipeTitle,
        String recipeImageUrl,
        Boolean isRecipeDeleted,
        List<ShoppingListItem> items
) {
    @Builder
    public record ShoppingListItem(
            Long id,
            String ingredient,
            String quantity,
            Boolean checked,
            Boolean isFromRecipe
    ) {
    }
}
