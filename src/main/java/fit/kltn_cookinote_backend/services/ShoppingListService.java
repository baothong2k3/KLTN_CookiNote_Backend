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

import fit.kltn_cookinote_backend.dtos.response.*;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface ShoppingListService {
    SyncShoppingListResponse createFromRecipe(Long userId, Long recipeId);

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

    /**
     * So sánh ShoppingList hiện tại của user với Recipe gốc và trả về sự khác biệt.
     * Không thực hiện thay đổi dữ liệu.
     *
     * @param userId   ID người dùng.
     * @param recipeId ID công thức.
     * @return Đối tượng chứa thông tin các mục thêm, xóa, cập nhật.
     */
    ShoppingListSyncCheckResponse checkRecipeUpdates(Long userId, Long recipeId);

    /**
     * Đánh dấu một mục trong danh sách mua sắm là đã hoàn thành (checked = true).
     *
     * @param userId ID của người dùng.
     * @param itemId ID của mục cần đánh dấu.
     * @return Thông tin mục đã được cập nhật.
     */
    ShoppingListResponse checkItem(Long userId, Long itemId);

    /**
     * Lấy danh sách các mục trong shopping list của người dùng.
     * Nếu recipeId có giá trị, lấy các mục cho recipe đó.
     * Nếu recipeId rỗng (empty Optional), lấy các mục lẻ loi (không thuộc recipe nào).
     *
     * @param userId   ID của người dùng.
     * @param recipeId Optional chứa ID của công thức, hoặc rỗng để lấy mục lẻ loi.
     * @return Danh sách các mục ShoppingListResponse.
     */
    List<ShoppingListResponse> getItems(Long userId, @Nullable Long recipeId);

    /**
     * Xóa các mục theo danh sách ID cụ thể.
     * Ném Exception nếu có ID không hợp lệ hoặc không thuộc về user.
     */
    Map<String, Integer> deleteItemsByIds(Long userId, List<Long> itemIds);

    /**
     * Xóa các mục shopping list dựa trên filter.
     *
     * @param userId   ID của người dùng.
     * @param filter   Loại filter ("checked", "recipe", "standalone", "all").
     * @param recipeId ID của recipe (chỉ dùng khi filter="recipe").
     * @return Map chứa số lượng mục đã xóa.
     */
    Map<String, Integer> deleteItemsByFilter(Long userId, String filter, @Nullable Long recipeId);
}
