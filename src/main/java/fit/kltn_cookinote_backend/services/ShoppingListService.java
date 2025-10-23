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

import fit.kltn_cookinote_backend.dtos.response.GroupedShoppingListResponse;
import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.dtos.response.RecipeSuggestionResponse;
import fit.kltn_cookinote_backend.dtos.response.ShoppingListResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ShoppingListService {
    List<ShoppingListResponse> createFromRecipe(Long userId, Long recipeId);

    // (1) Thêm 1 nguyên liệu lẻ loi (recipe = null)
    ShoppingListResponse upsertOneStandalone(Long userId, String ingredient, String quantity);

    // (2) Thêm 1 nguyên liệu vào list có recipe_id (áp dụng quy tắc merge giữ checked)
    ShoppingListResponse upsertOneInRecipe(Long userId, Long recipeId, String ingredient, String quantity);

    // Cập nhật nội dung 1 item (ingredient, quantity, checked)
    ShoppingListResponse updateItemContent(Long userId, Long itemId,
                                           String newIngredientOrNull,
                                           String newQuantityOrNull,
                                           Boolean newCheckedOrNull);

    // Chuyển 1 item (ingredient) từ list này sang list khác (có thể là null)
    ShoppingListResponse moveItem(Long userId, Long itemId, Long targetRecipeIdOrNull);

    /**
     * Lấy toàn bộ danh sách mua sắm của người dùng,
     * gom nhóm theo công thức (recipe).
     *
     * @param userId ID của người dùng.
     * @return Danh sách đã được gom nhóm.
     */
    List<GroupedShoppingListResponse> getAllGroupedByRecipe(Long userId);

    PageResult<RecipeSuggestionResponse> suggestRecipes(Long userId, List<String> ingredientNames, Pageable pageable);
}
