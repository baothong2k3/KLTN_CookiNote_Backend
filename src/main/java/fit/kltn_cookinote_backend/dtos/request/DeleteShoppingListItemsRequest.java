/*
 * @ (#) DeleteShoppingListItemsRequest.java    1.0    28/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO chứa danh sách ID của các mục shopping list cần xóa.
 */
public record DeleteShoppingListItemsRequest(
        @NotEmpty(message = "Danh sách ID không được rỗng.")
        List<Long> itemIds
) {
}
