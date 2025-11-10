/*
 * @ (#) DailyMenuRecipeCardResponse.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Difficulty;

import java.io.Serializable;

// DTO mới này là bản sao của RecipeCardResponse nhưng implement Serializable
public record DailyMenuRecipeCardResponse(
        Long id,
        String title,
        String description,
        String imageUrl,
        Integer prepareTime,
        Integer cookTime,
        Difficulty difficulty,
        Double averageRating,
        String ownerName,
        String ownerAvatar
) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public static DailyMenuRecipeCardResponse from(Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        User owner = recipe.getUser();
        String ownerName = (owner != null && owner.getDisplayName() != null) ? owner.getDisplayName() : "Ẩn danh";
        String ownerAvatar = (owner != null) ? owner.getAvatarUrl() : null;

        return new DailyMenuRecipeCardResponse(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getImageUrl(),
                recipe.getPrepareTime(),
                recipe.getCookTime(),
                recipe.getDifficulty(),
                recipe.getAverageRating(),
                ownerName,
                ownerAvatar
        );
    }
}
