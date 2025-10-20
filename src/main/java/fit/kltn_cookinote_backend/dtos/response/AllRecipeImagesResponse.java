/*
 * @ (#) AllRecipeImagesResponse.java    1.0    14/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 14/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeCoverImageHistory;
import fit.kltn_cookinote_backend.entities.RecipeStepImage;
import lombok.Builder;

import java.util.List;
import java.util.stream.Collectors;

@Builder
public record AllRecipeImagesResponse(
        List<RecipeCoverImageHistoryDto> coverImages,
        List<RecipeStepImageDto> stepImages
) {

    @Builder
    public record RecipeCoverImageHistoryDto(
            Long id,
            String imageUrl,
            boolean active
    ) {
        public static RecipeCoverImageHistoryDto from(RecipeCoverImageHistory history) {
            return new RecipeCoverImageHistoryDto(history.getId(), history.getImageUrl(), history.isActive());
        }
    }

    @Builder
    public record RecipeStepImageDto(
            Long id,
            Long stepId,
            String imageUrl,
            boolean active
    ) {
        public static RecipeStepImageDto from(RecipeStepImage image) {
            return new RecipeStepImageDto(image.getId(), image.getStep().getId(), image.getImageUrl(), image.isActive());
        }
    }

    public static AllRecipeImagesResponse from(List<RecipeCoverImageHistory> coverHistories, List<RecipeStepImage> stepImages) {
        List<RecipeCoverImageHistoryDto> coverDtos = coverHistories.stream()
                .map(RecipeCoverImageHistoryDto::from)
                .collect(Collectors.toList());

        List<RecipeStepImageDto> stepDtos = stepImages.stream()
                .map(RecipeStepImageDto::from)
                .collect(Collectors.toList());

        return new AllRecipeImagesResponse(coverDtos, stepDtos);
    }
}
