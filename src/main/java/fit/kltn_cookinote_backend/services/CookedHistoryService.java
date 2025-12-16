/*
 * @ (#) CookedHistoryService.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.CookedHistoryResponse;

import java.util.List;

public interface CookedHistoryService {
    /**
     * Đánh dấu một công thức là đã được nấu bởi người dùng.
     *
     * @param userId   ID người dùng thực hiện.
     * @param recipeId ID công thức đã nấu.
     * @return Thông tin về bản ghi lịch sử vừa tạo.
     */
    CookedHistoryResponse markRecipeAsCooked(Long userId, Long recipeId);

    /**
     * Lấy danh sách lịch sử các món đã nấu của người dùng.
     *
     * @param userId     ID người dùng.
     * @param categoryId ID danh mục để lọc (có thể null).
     * @return Danh sách các món đã nấu, sắp xếp mới nhất trước.
     */
    List<CookedHistoryResponse> getCookedHistory(Long userId, Long categoryId);
}
