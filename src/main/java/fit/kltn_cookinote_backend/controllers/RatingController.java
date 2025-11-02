/*
 * @ (#) RatingController.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RateRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.RatingResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.RatingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recipes/{recipeId}/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    /**
     * API để người dùng thêm hoặc cập nhật đánh giá cho một công thức.
     * Endpoint: PUT /recipes/{recipeId}/ratings/me
     * (Dùng PUT vì đây là hành động tạo mới hoặc cập nhật toàn bộ đánh giá của user đó)
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RatingResponse>> rateRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @Valid @RequestBody RateRecipeRequest request,
            HttpServletRequest httpReq) {

        RatingResponse data = ratingService.rateRecipe(authUser.getUserId(), recipeId, request);
        String message = "Đánh giá công thức thành công.";
        return ResponseEntity.ok(ApiResponse.success(message, data, httpReq.getRequestURI()));
    }

    /**
     * API để người dùng xóa đánh giá của chính mình cho một công thức.
     * Endpoint: DELETE /recipes/{recipeId}/ratings/me
     */
    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteMyRating(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq) {

        ratingService.deleteMyRating(authUser.getUserId(), recipeId);
        String message = "Đã xóa đánh giá của bạn cho công thức thành công.";
        return ResponseEntity.ok(ApiResponse.success(message, httpReq.getRequestURI()));
    }
}
