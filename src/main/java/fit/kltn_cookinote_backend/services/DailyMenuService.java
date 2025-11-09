/*
 * @ (#) DailyMenuService.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.DailyMenuResponse;

import java.time.LocalDate;

public interface DailyMenuService {
    /**
     * Lấy thực đơn hằng ngày đã lưu cho một ngày cụ thể.
     * Nếu chưa tồn tại, hệ thống sẽ tạo mới, lưu lại và trả về.
     *
     * @param userId ID người dùng
     * @param date   Ngày cần xem
     * @return Thực đơn hằng ngày
     */
    DailyMenuResponse getOrGenerateDailyMenu(Long userId, LocalDate date);
}