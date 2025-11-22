/*
 * @ (#) LoginHistoryService.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.dtos.response.UserLoginHistoryResponse;
import fit.kltn_cookinote_backend.entities.User;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface LoginHistoryService {
    /**
     * Lưu lịch sử đăng nhập của user.
     * Hàm này sẽ tự động lấy IP và User-Agent từ RequestContext hiện tại.
     *
     * @param user Người dùng vừa đăng nhập thành công.
     */
    void save(User user);

    PageResult<UserLoginHistoryResponse> getAllLoginHistory(LocalDate date, Pageable pageable);

    PageResult<UserLoginHistoryResponse> getUserLoginHistory(Long userId, LocalDate date, Pageable pageable);

    PageResult<UserLoginHistoryResponse> getMyLoginHistory(Long userId, LocalDate date, Pageable pageable);
}
