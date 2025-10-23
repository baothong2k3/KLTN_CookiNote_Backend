/*
 * @ (#) ShoppingListSyncCheckResponse.java    1.0    23/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 23/10/2025
 * @version: 1.0
 */

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO chứa kết quả so sánh giữa ShoppingList hiện tại và Recipe gốc.
 */
@Getter
@Setter
@Builder
public class ShoppingListSyncCheckResponse {
    /**
     * Các nguyên liệu mới có trong Recipe nhưng chưa có trong ShoppingList.
     */
    private List<SyncItem> addedItems;

    /**
     * Các nguyên liệu có trong ShoppingList (isFromRecipe=true) nhưng đã bị xóa khỏi Recipe.
     */
    private List<SyncItem> removedItems;

    /**
     * Các nguyên liệu có trong cả hai nhưng khác nhau về quantity.
     */
    private List<UpdatedSyncItem> updatedItems;

    /**
     * Các nguyên liệu có trong ShoppingList (isFromRecipe=false) mà không có trong Recipe (mục tự thêm).
     * Thông tin này có thể không cần thiết cho frontend, nhưng backend có thể trả về để đầy đủ.
     */
    private List<SyncItem> manualItemsNotInRecipe;

    /**
     * Kiểm tra xem có bất kỳ thay đổi nào không.
     */
    public boolean hasChanges() {
        return (addedItems != null && !addedItems.isEmpty()) ||
                (removedItems != null && !removedItems.isEmpty()) ||
                (updatedItems != null && !updatedItems.isEmpty());
    }


    // --- Lớp con cho các mục ---

    /**
     * Đại diện cho một nguyên liệu (thêm mới, xóa, tự thêm).
     */
    @Getter
    @Setter
    @Builder
    public static class SyncItem {
        private Long shoppingListId; // ID của mục trong shopping list (nếu có, ví dụ khi xóa)
        private String ingredient;
        private String quantity;
    }

    /**
     * Đại diện cho nguyên liệu bị thay đổi số lượng.
     */
    @Getter
    @Setter
    @Builder
    public static class UpdatedSyncItem {
        private Long shoppingListId; // ID của mục trong shopping list
        private String ingredient;
        private String oldQuantity; // Số lượng cũ trong shopping list
        private String newQuantity; // Số lượng mới trong recipe
    }
}
