/*
 * @ (#) RecipeStepItem.java    1.0    14/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 14/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeStep;
import fit.kltn_cookinote_backend.entities.RecipeStepImage;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Builder
public record RecipeStepItem(
        Long id,
        Integer stepNo,
        String content,
        Integer suggestedTime,
        String tips,
        List<String> images
) {
    public static RecipeStepItem from(RecipeStep s) {
        List<String> urls = new ArrayList<>();
        if (s.getImages() != null) {
            // Lọc và chỉ lấy những ảnh có cờ active = true
            s.getImages().stream()
                    .filter(RecipeStepImage::isActive)
                    .forEach(img -> urls.add(img.getImageUrl()));
        }
        return RecipeStepItem.builder()
                .id(s.getId())
                .stepNo(s.getStepNo())
                .content(s.getContent())
                .suggestedTime(s.getSuggestedTime())
                .tips(s.getTips())
                .images(urls)
                .build();
    }
}
