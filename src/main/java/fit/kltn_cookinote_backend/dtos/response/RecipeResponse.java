/*
 * @ (#) RecipeResponse.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.enums.Privacy;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Builder
public record RecipeResponse(
        Long id,
        Long ownerId,
        String ownerName,
        Privacy privacy,
        Long categoryId,
        String title,
        String description,
        Integer prepareTime,
        Integer cookTime,
        Difficulty difficulty,
        String imageUrl,
        Long view,
        LocalDateTime createdAt,
        List<IngredientDto> ingredients,
        List<StepDto> steps
) {
    @Builder
    public record IngredientDto(Long id, String name, String quantity) {
    }

    @Builder
    public record StepDto(Long id, Integer stepNo, String content, Integer suggestedTime, String tips,
                          List<String> images) {
    }

    public static RecipeResponse from(Recipe r) {
        // ingredients
        List<IngredientDto> ingDtos = new ArrayList<>();
        if (r.getIngredients() != null) {
            for (RecipeIngredient i : r.getIngredients()) {
                ingDtos.add(IngredientDto.builder()
                        .id(i.getId())
                        .name(i.getName())
                        .quantity(i.getQuantity())
                        .build());
            }
        }

        // steps (sắp theo stepNo ASC); images nếu có
        List<StepDto> stepDtos = new ArrayList<>();
        if (r.getSteps() != null) {
            r.getSteps().stream()
                    .sorted(Comparator.comparing(RecipeStep::getStepNo, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(s -> {
                        List<String> imgs = new ArrayList<>();
                        if (s.getImages() != null) {
                            // Lọc và chỉ lấy những ảnh có cờ active = true
                            s.getImages().stream()
                                    .filter(RecipeStepImage::isActive)
                                    .forEach(si -> imgs.add(si.getImageUrl()));
                        }
                        stepDtos.add(StepDto.builder()
                                .id(s.getId())
                                .stepNo(s.getStepNo())
                                .content(s.getContent())
                                .suggestedTime(s.getSuggestedTime())
                                .tips(s.getTips())
                                .images(imgs)
                                .build());
                    });
        }

        return RecipeResponse.builder()
                .id(r.getId())
                .ownerId(r.getUser() != null ? r.getUser().getUserId() : null)
                .ownerName(r.getUser() != null ? r.getUser().getDisplayName() : null)
                .privacy(r.getPrivacy())
                .categoryId(r.getCategory() != null ? r.getCategory().getId() : null)
                .title(r.getTitle())
                .description(r.getDescription())
                .prepareTime(r.getPrepareTime())
                .cookTime(r.getCookTime())
                .difficulty(r.getDifficulty())
                .imageUrl(r.getImageUrl())
                .view(r.getView())
                .createdAt(r.getCreatedAt())
                .ingredients(ingDtos)
                .steps(stepDtos)
                .build();
    }
}