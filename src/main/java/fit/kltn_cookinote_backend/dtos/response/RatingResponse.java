/*
 * @ (#) RatingResponse.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeRating;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record RatingResponse(
        Long ratingId,
        Long userId,
        Long recipeId,
        Integer score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RatingResponse from(RecipeRating rating) {
        return RatingResponse.builder()
                .ratingId(rating.getId())
                .userId(rating.getUser().getUserId())
                .recipeId(rating.getRecipe().getId())
                .score(rating.getScore())
                .createdAt(rating.getCreatedAt())
                .updatedAt(rating.getUpdatedAt())
                .build();
    }
}
