/*
 * @ (#) AiController.java    1.0    08/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 08/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.GenerateRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.GeneratedRecipeResponse;
import fit.kltn_cookinote_backend.services.AiRecipeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiRecipeService aiRecipeService;

    /**
     * API để yêu cầu AI tạo một công thức mới dựa trên tên món ăn.
     * Dữ liệu trả về (GeneratedRecipeResponse) có cấu trúc tương tự RecipeCreateRequest
     * và có thể được dùng để điền vào form tạo công thức.
     * <p>
     * Endpoint: POST /ai/generate-recipe
     */
    @PostMapping("/generate-recipe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GeneratedRecipeResponse>> generateRecipe(
            @Valid @RequestBody GenerateRecipeRequest request,
            HttpServletRequest httpReq
    ) {
        // Gọi service để lấy dữ liệu từ AI
        GeneratedRecipeResponse data = aiRecipeService.generateRecipe(request);

        return ResponseEntity.ok(ApiResponse.success(
                "AI đã tạo công thức thành công. Vui lòng xem lại trước khi lưu.",
                data,
                httpReq.getRequestURI()
        ));
    }
}
