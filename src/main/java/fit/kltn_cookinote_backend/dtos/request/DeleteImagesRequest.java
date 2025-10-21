/*
 * @ (#) DeleteImagesRequest.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import java.util.List;

/**
 * DTO chứa danh sách ID của các ảnh (cover và step) cần xóa.
 * Chỉ những ảnh có active = false mới được xử lý.
 * Các danh sách là tùy chọn, nhưng ít nhất một danh sách phải được cung cấp
 * và chứa ID để thực hiện hành động.
 */
public record DeleteImagesRequest(
        // Danh sách ID từ bảng recipe_cover_image_history (tùy chọn)
        List<Long> coverImageIds,

        // Danh sách ID từ bảng recipe_step_image (tùy chọn)
        List<Long> stepImageIds
) {
}
