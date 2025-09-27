/*
 * @ (#) ShoppingListController.java    1.0    28/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.ShoppingListResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.ShoppingListService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/shopping-lists")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    /**
     * Tạo shopping list cho user hiện tại bằng toàn bộ RecipeIngredient của Recipe
     * - Ghi đè danh sách cũ của (user, recipe) nếu có.
     */
    @PostMapping("/recipes/{recipeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ShoppingListResponse>>> createFromRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest req
    ) {
        List<ShoppingListResponse> data = shoppingListService.createFromRecipe(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Tạo shopping list từ recipe #" + recipeId + " thành công (" + data.size() + " mục).", data, req.getRequestURI()));
    }
}
