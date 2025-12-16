/*
 * @ (#) CookedHistoryController.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.CookedHistoryResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.CookedHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cooked-history")
public class CookedHistoryController {

    private final CookedHistoryService cookedHistoryService;

    /**
     * API để đánh dấu một công thức là đã được nấu bởi người dùng hiện tại.
     * Endpoint: POST /recipes/{recipeId}/cooked
     */
    @PostMapping("/recipes/{recipeId}/cooked")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CookedHistoryResponse>> markAsCooked(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq) {

        CookedHistoryResponse data = cookedHistoryService.markRecipeAsCooked(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm vào lịch sử nấu ăn", data, httpReq.getRequestURI()));
    }

    /**
     * API để lấy lịch sử các món đã nấu của người dùng hiện tại.
     * Endpoint: GET /cooked-history/me?categoryId=...
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CookedHistoryResponse>>> getMyCookedHistory(
            @AuthenticationPrincipal User authUser,
            @RequestParam(required = false) Long categoryId, // [NEW] Param tùy chọn
            HttpServletRequest httpReq) {

        List<CookedHistoryResponse> data = cookedHistoryService.getCookedHistory(authUser.getUserId(), categoryId);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử nấu ăn thành công", data, httpReq.getRequestURI()));
    }
}
