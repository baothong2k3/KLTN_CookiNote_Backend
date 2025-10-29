/*
 * @ (#) SyncShoppingListResponse.java    1.0    24/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 24/10/2025
 * @version: 1.0
 */

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class SyncShoppingListResponse {
    private final List<ShoppingListResponse> items;
    private final boolean existedBefore;
}
