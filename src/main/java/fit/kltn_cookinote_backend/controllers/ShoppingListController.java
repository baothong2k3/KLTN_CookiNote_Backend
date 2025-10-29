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

import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.ShoppingListService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/shopping-lists")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    /**
     * Tạo hoặc Đồng bộ shopping list cho user hiện tại từ RecipeIngredient của Recipe.
     */
    @PostMapping("/recipes/{recipeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ShoppingListResponse>>> createFromRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest req
    ) {
        // (1) Nhận về DTO mới
        SyncShoppingListResponse syncResponse = shoppingListService.createFromRecipe(authUser.getUserId(), recipeId);

        // (2) Quyết định message dựa trên cờ existedBefore
        String message;
        if (syncResponse.isExistedBefore()) {
            // Đây là hành động "Đồng bộ"
            message = String.format("Đồng bộ shopping list từ recipe #%d thành công (%d mục).", recipeId, syncResponse.getItems().size());
        } else {
            // Đây là hành động "Tạo mới"
            message = String.format("Tạo shopping list từ recipe #%d thành công (%d mục).", recipeId, syncResponse.getItems().size());
        }

        return ResponseEntity.ok(ApiResponse.success(message, syncResponse.getItems(), req.getRequestURI()));
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

    /**
     * Chuyển 1 item (ingredient) từ list này sang list khác (có thể là null)
     */
    @PatchMapping("/items/{itemId}/move")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShoppingListResponse>> moveItem(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long itemId,
            @RequestBody(required = false) ShoppingListMoveRequest req,
            HttpServletRequest http
    ) {
        ShoppingListResponse data = shoppingListService.moveItem(
                authUser.getUserId(),
                itemId,
                req == null ? null : req.recipeId()
        );
        return ResponseEntity.ok(ApiResponse.success("Di chuyển nguyên liệu thành công.", data, http.getRequestURI()));
    }

    /**
     * Lấy toàn bộ danh sách mua sắm của người dùng, gom nhóm theo công thức.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<GroupedShoppingListResponse>>> getAllShoppingLists(
            @AuthenticationPrincipal User authUser,
            HttpServletRequest req
    ) {
        List<GroupedShoppingListResponse> data = shoppingListService.getAllGroupedByRecipe(authUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách mua sắm thành công", data, req.getRequestURI()));
    }

    /**
     * Gợi ý công thức dựa trên danh sách nguyên liệu từ shopping list.
     */
    @PostMapping("/suggest-recipes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResult<RecipeSuggestionResponse>>> suggestRecipes(
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody SuggestRecipesRequest req,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            HttpServletRequest httpReq
    ) {
        if (req.ingredientNames() == null || req.ingredientNames().isEmpty()) {
            throw new IllegalArgumentException("Danh sách nguyên liệu không được để trống.");
        }

        // Tạo đối tượng Pageable
        Pageable pageable = PageRequest.of(page, size);

        // Gọi service với Pageable
        PageResult<RecipeSuggestionResponse> suggestions = shoppingListService.suggestRecipes(
                authUser.getUserId(),
                req.ingredientNames(),
                pageable
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Gợi ý công thức thành công",
                suggestions,
                httpReq.getRequestURI()
        ));
    }

    /**
     * Kiểm tra sự khác biệt giữa ShoppingList hiện tại và Recipe gốc.
     * Không thay đổi dữ liệu.
     */
    @GetMapping("/recipes/{recipeId}/check-updates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShoppingListSyncCheckResponse>> checkRecipeUpdates(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest req
    ) {
        ShoppingListSyncCheckResponse data = shoppingListService.checkRecipeUpdates(authUser.getUserId(), recipeId);
        String message = data.hasChanges() ? "Phát hiện thay đổi giữa danh sách mua sắm và công thức." : "Danh sách mua sắm đã khớp với công thức.";
        return ResponseEntity.ok(ApiResponse.success(message, data, req.getRequestURI()));
    }

    /**
     * Đánh dấu một mục trong shopping list là đã hoàn thành (checked = true).
     * Sử dụng PATCH vì chỉ cập nhật một phần trạng thái.
     */
    @PatchMapping("/items/{itemId}/check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShoppingListResponse>> checkShoppingListItem(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long itemId,
            HttpServletRequest httpReq
    ) {
        ShoppingListResponse data = shoppingListService.checkItem(authUser.getUserId(), itemId);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu mục là hoàn thành.", data, httpReq.getRequestURI()));
    }

    /**
     * Xóa một hoặc nhiều mục khỏi shopping list theo danh sách ID cụ thể.
     * Endpoint: DELETE /shopping-lists/items-by-ids
     */
    @DeleteMapping("/items-by-ids")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> deleteShoppingListItemsByIds(
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody DeleteShoppingListItemsRequest reqBody, // Nhận ID từ body
            HttpServletRequest httpReq
    ) {
        // Validate IDs trước khi xóa
        if (reqBody.itemIds() == null || reqBody.itemIds().isEmpty()) {
            throw new IllegalArgumentException("Danh sách ID cần xóa không được rỗng.");
        }

        Map<String, Integer> result = shoppingListService.deleteItemsByIds(authUser.getUserId(), reqBody.itemIds());
        int deletedCount = result.getOrDefault("deletedCount", 0);
        String message = String.format("Đã xóa thành công %d mục được chọn.", deletedCount);

        return ResponseEntity.ok(ApiResponse.success(message, result, httpReq.getRequestURI()));
    }

    /**
     * Xóa hàng loạt các mục shopping list theo filter.
     * Endpoint: DELETE /shopping-lists/items-by-filter?filter=...[&recipeId=...]
     * Filters hợp lệ: "checked", "recipe", "standalone", "all".
     */
    @DeleteMapping("/items-by-filter")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> deleteShoppingListItemsByFilter(
            @AuthenticationPrincipal User authUser,
            @RequestParam String filter, // Bắt buộc phải có filter
            @RequestParam(required = false) Long recipeId, // Chỉ cần khi filter=recipe
            HttpServletRequest httpReq
    ) {
        Map<String, Integer> result = shoppingListService.deleteItemsByFilter(authUser.getUserId(), filter, recipeId);
        int deletedCount = result.getOrDefault("deletedCount", 0);
        String message;

        // Tạo message dựa trên filter
        message = switch (filter.toLowerCase()) {
            case "checked" -> String.format("Đã xóa %d mục đã hoàn thành.", deletedCount);
            case "recipe" -> {
                if (recipeId == null) { // Bắt lỗi nếu recipeId thiếu khi filter=recipe (dù service đã check)
                    throw new IllegalArgumentException("Cần cung cấp 'recipeId' khi filter='recipe'.");
                }
                yield String.format("Đã xóa %d mục thuộc Recipe ID %d.", deletedCount, recipeId);
            }
            case "standalone" -> String.format("Đã xóa %d mục lẻ loi.", deletedCount);
            case "all" -> String.format("Đã xóa tất cả %d mục.", deletedCount);
            default ->
                    throw new IllegalArgumentException("Giá trị 'filter' không hợp lệ: " + filter); // Bắt filter không hợp lệ
        };

        return ResponseEntity.ok(ApiResponse.success(message, result, httpReq.getRequestURI()));
    }

    /**
     * Lấy danh sách các mục shopping list.
     * Có thể lọc theo recipeId (dùng ?recipeId=...) hoặc lấy các mục lẻ loi (không truyền recipeId).
     */
    @GetMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ShoppingListResponse>>> getShoppingListItems(
            @AuthenticationPrincipal User authUser,
            @RequestParam(required = false) Long recipeId,
            HttpServletRequest httpReq
    ) {
        List<ShoppingListResponse> data = shoppingListService.getItems(authUser.getUserId(), recipeId);

        String message = (recipeId != null)
                ? String.format("Lấy danh sách mua sắm cho Recipe #%d thành công.", recipeId)
                : "Lấy danh sách mua sắm lẻ loi thành công.";

        return ResponseEntity.ok(ApiResponse.success(message, data, httpReq.getRequestURI()));
    }
}
