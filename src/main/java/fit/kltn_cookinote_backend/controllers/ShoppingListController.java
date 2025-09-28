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

import fit.kltn_cookinote_backend.dtos.request.ShoppingListUpdateRequest;
import fit.kltn_cookinote_backend.dtos.request.ShoppingListUpsertRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.ShoppingListResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.ShoppingListService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    /**
     * (1) Thêm 1 nguyên liệu lẻ loi (recipe=null)
     */
    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShoppingListResponse>> upsertOneStandalone(
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody ShoppingListUpsertRequest req,
            HttpServletRequest http
    ) {
        ShoppingListResponse data = shoppingListService.upsertOneStandalone(
                authUser.getUserId(), req.ingredient(), req.quantity()
        );
        return ResponseEntity.ok(ApiResponse.success("Thêm nguyên liệu lẻ loi thành công.", data, http.getRequestURI()));
    }

    /**
     * (2) Thêm 1 nguyên liệu vào list có recipe_id (áp dụng merge giữ checked)
     */
    @PostMapping("/recipes/{recipeId}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShoppingListResponse>> upsertOneInRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @Valid @RequestBody ShoppingListUpsertRequest req,
            HttpServletRequest http
    ) {
        ShoppingListResponse data = shoppingListService.upsertOneInRecipe(
                authUser.getUserId(), recipeId, req.ingredient(), req.quantity()
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Thêm nguyên liệu vào recipe #" + recipeId + " thành công.",
                data, http.getRequestURI()
        ));
    }

    /**
     * Cập nhật nguyên liệu trong shopping list (ingredient, quantity, checked)
     */
    @PutMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShoppingListResponse>> updateItemContent(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long itemId,
            @Valid @RequestBody ShoppingListUpdateRequest req,
            HttpServletRequest http
    ) {
        var data = shoppingListService.updateItemContent(
                authUser.getUserId(),
                itemId,
                req.ingredient(),
                req.quantity(),
                req.checked()
        );
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nguyên liệu thành công.", data, http.getRequestURI()));
    }
}
