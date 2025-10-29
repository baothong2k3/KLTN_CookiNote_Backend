/*
 * @ (#) RatingService.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RateRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.RatingResponse;

public interface RatingService {
    /**
     * Thêm hoặc cập nhật rating cho một công thức.
     *
     * @param userId   ID của người dùng đánh giá.
     * @param recipeId ID của công thức được đánh giá.
     * @param request  DTO chứa điểm đánh giá.
     * @return Thông tin rating đã được lưu.
     */
    RatingResponse rateRecipe(Long userId, Long recipeId, RateRecipeRequest request);

    /**
     * Tính toán và cập nhật điểm trung bình và số lượt đánh giá cho công thức.
     *
     * @param recipeId ID của công thức cần cập nhật.
     */
    void updateRecipeRatingStats(Long recipeId);
}