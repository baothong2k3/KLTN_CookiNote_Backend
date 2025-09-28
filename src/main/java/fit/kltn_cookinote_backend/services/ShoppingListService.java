/*
 * @ (#) ShoppingListService.java    1.0    27/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.ShoppingListResponse;

import java.util.List;

public interface ShoppingListService {
    List<ShoppingListResponse> createFromRecipe(Long userId, Long recipeId);

    // (1) Thêm 1 nguyên liệu lẻ loi (recipe = null)
    ShoppingListResponse upsertOneStandalone(Long userId, String ingredient, String quantity);
}
