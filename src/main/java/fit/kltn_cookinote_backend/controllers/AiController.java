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

import fit.kltn_cookinote_backend.dtos.request.ChatRequest;
import fit.kltn_cookinote_backend.dtos.request.GenerateRecipeRequest;
import fit.kltn_cookinote_backend.dtos.request.ImportRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.ChatResponse;
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

    /**
     * API Tùy chọn: Làm giàu (Enrich) dữ liệu công thức thô.
     * Dùng khi user đã import từ URL nhưng muốn AI điền thêm các thông tin còn thiếu.
     * <p>
     * Endpoint: POST /ai/enrich-recipe
     */
    @PostMapping("/enrich-recipe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GeneratedRecipeResponse>> enrichRecipe(
            @RequestBody GeneratedRecipeResponse rawData,
            HttpServletRequest httpReq
    ) {
        GeneratedRecipeResponse data = aiRecipeService.enrichRecipe(rawData);
        return ResponseEntity.ok(ApiResponse.success(
                "AI đã bổ sung và chuẩn hóa thông tin công thức.", data, httpReq.getRequestURI()));
    }

    /**
     * API Chatbot nấu ăn.
     * Endpoint: POST /ai/chat
     */
    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatResponse>> chatWithAi(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpReq
    ) {
        ChatResponse data = aiRecipeService.chatWithAi(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Trả lời thành công.",
                data,
                httpReq.getRequestURI()
        ));
    }

    @PostMapping("/import-from-url")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<GeneratedRecipeResponse>> importRecipeFromUrl(
            @Valid @RequestBody ImportRecipeRequest req,
            HttpServletRequest httpReq
    ) {
        GeneratedRecipeResponse data = aiRecipeService.importFromUrl(req);
        return ResponseEntity.ok(ApiResponse.success("AI đã phân tích công thức thành công từ URL", data, httpReq.getRequestURI()));
    }
}
