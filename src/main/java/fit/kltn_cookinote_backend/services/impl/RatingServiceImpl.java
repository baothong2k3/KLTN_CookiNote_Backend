/*
 * @ (#) RatingServiceImpl.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RateRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.RatingResponse;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeRating;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.RecipeRatingRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.RatingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final RecipeRatingRepository ratingRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RatingResponse rateRecipe(Long userId, Long recipeId, RateRecipeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng: " + userId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy công thức: " + recipeId));

        // Kiểm tra quyền xem công thức trước khi cho phép rating
        Long ownerId = recipe.getUser() != null ? recipe.getUser().getUserId() : null;
        if (!canView(recipe.getPrivacy(), ownerId, userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này để đánh giá.");
        }

        // Kiểm tra công thức đã bị xóa chưa
        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Không thể đánh giá công thức đã bị xóa.");
        }

        // Tìm rating cũ (nếu có)
        Optional<RecipeRating> existingRatingOpt = ratingRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId);

        RecipeRating rating;
        if (existingRatingOpt.isPresent()) {
            // Cập nhật rating cũ
            rating = existingRatingOpt.get();
            rating.setScore(request.score());
        } else {
            // Tạo rating mới
            rating = RecipeRating.builder()
                    .user(user)
                    .recipe(recipe)
                    .score(request.score())
                    .build();
        }

        RecipeRating savedRating = ratingRepository.save(rating);

        // Cập nhật lại điểm trung bình và số lượt rating cho Recipe
        updateRecipeRatingStats(recipeId);

        return RatingResponse.from(savedRating);
    }

    @Override
    @Transactional
    public void updateRecipeRatingStats(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy công thức: " + recipeId));

        Optional<Object[]> statsOpt = ratingRepository.calculateRatingStats(recipeId);

        if (statsOpt.isPresent()) {
            Object[] outerArray = statsOpt.get();

            // Sử dụng pattern variable (Java 16+) để kiểm tra và lấy mảng bên trong
            if (outerArray.length == 1 && outerArray[0] instanceof Object[] statsArray) {

                // Kiểm tra mảng bên trong có đúng cấu trúc [SUM, COUNT] không
                if (statsArray.length == 2 && statsArray[0] != null && statsArray[1] != null) {
                    long totalScore = ((Number) statsArray[0]).longValue();
                    long count = ((Number) statsArray[1]).longValue();

                    if (count > 0) {
                        double average = (double) totalScore / count;
                        // Làm tròn đến 0.5 gần nhất
                        double roundedAverage = Math.round(average * 2) / 2.0;

                        recipe.setAverageRating(roundedAverage);
                        recipe.setRatingCount((int) count);
                    } else {
                        // Nếu count là 0, reset stats
                        recipe.setAverageRating(0.0);
                        recipe.setRatingCount(0);
                    }
                } else {
                    // Mảng bên trong không đúng định dạng -> reset stats
                    recipe.setAverageRating(0.0);
                    recipe.setRatingCount(0);
                }
            } else {
                // Mảng bên ngoài không đúng định dạng -> reset stats
                recipe.setAverageRating(0.0);
                recipe.setRatingCount(0);
            }
        } else {
            // Query không trả về kết quả (không có rating nào) -> reset stats
            recipe.setAverageRating(0.0);
            recipe.setRatingCount(0);
        }

        // Lưu lại Recipe với thông tin rating đã cập nhật
        recipeRepository.save(recipe);
    }

    @Override
    @Transactional
    public void deleteMyRating(Long userId, Long recipeId) {
        userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        recipeRepository.findById(recipeId).orElseThrow(() -> new EntityNotFoundException("Recipe not found"));

        // Tìm đánh giá hiện có của người dùng cho công thức này
        Optional<RecipeRating> ratingOpt = ratingRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId);

        if (ratingOpt.isPresent()) {
            // Nếu tìm thấy, xóa nó đi
            ratingRepository.delete(ratingOpt.get());

            // Sau khi xóa, cập nhật lại thống kê rating cho công thức
            updateRecipeRatingStats(recipeId);
        }
    }

    // Helper kiểm tra quyền xem
    private boolean canView(Privacy privacy, Long ownerId, Long viewerId) {
        return switch (privacy) {
            case PUBLIC, SHARED -> true;
            case PRIVATE -> viewerId != null && viewerId.equals(ownerId);
        };
    }
}
