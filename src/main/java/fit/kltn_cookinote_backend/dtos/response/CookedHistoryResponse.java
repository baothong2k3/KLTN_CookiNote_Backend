/*
 * @ (#) CookedHistoryResponse.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.CookedHistory;
import fit.kltn_cookinote_backend.entities.Recipe; // <<< Thêm import Recipe
import fit.kltn_cookinote_backend.entities.User; // <<< Thêm import User
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CookedHistoryResponse(
        Long id,
        Long userId, // ID của người nấu
        String ownerName, // Tên người sở hữu Recipe
        Long recipeId, // Có thể null nếu recipe đã bị xóa
        String recipeTitle, // Luôn có tên (tên gốc nếu đã xóa)
        String recipeImageUrl, // Có thể null nếu recipe đã bị xóa
        LocalDateTime cookedAt,
        String difficulty, // Độ khó (String)
        Long view, // Lượt xem
        Integer prepareTime, // Thời gian chuẩn bị
        Integer cookTime, // Thời gian nấu
        Boolean isRecipeDeleted
) {
    public static CookedHistoryResponse from(CookedHistory entity) {
        Recipe recipe = entity.getRecipe(); // Lấy đối tượng Recipe liên kết
        User recipeOwner = (recipe != null) ? recipe.getUser() : null; // Lấy chủ sở hữu recipe

        boolean isDeleted = Boolean.TRUE.equals(entity.getIsRecipeDeleted());

        String title = isDeleted
                ? "[ĐÃ XÓA] " + entity.getOriginalRecipeTitle()
                : (recipe != null ? recipe.getTitle() : "[Lỗi dữ liệu]");

        String imageUrl = isDeleted
                ? null
                : (recipe != null ? recipe.getImageUrl() : null);

        // Lấy thông tin từ Recipe (nếu tồn tại và không bị xóa)
        String ownerName = (!isDeleted && recipeOwner != null) ? recipeOwner.getDisplayName() : null;
        String difficultyStr = (!isDeleted && recipe != null && recipe.getDifficulty() != null) ? recipe.getDifficulty().name() : null;
        Long viewCount = (!isDeleted && recipe != null) ? recipe.getView() : null;
        Integer prepTime = (!isDeleted && recipe != null) ? recipe.getPrepareTime() : null;
        Integer cookingTime = (!isDeleted && recipe != null) ? recipe.getCookTime() : null;

        return CookedHistoryResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getUserId() : null) // ID người nấu
                .ownerName(ownerName)
                .recipeId(recipe != null ? recipe.getId() : null)
                .recipeTitle(title)
                .recipeImageUrl(imageUrl)
                .cookedAt(entity.getCookedAt())
                .difficulty(difficultyStr)
                .view(viewCount)
                .prepareTime(prepTime)
                .cookTime(cookingTime)
                .isRecipeDeleted(isDeleted) // Giữ nguyên cờ isRecipeDeleted từ entity
                .build();
    }
}
