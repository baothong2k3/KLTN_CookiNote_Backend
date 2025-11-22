/*
 * @ (#) UserLoginHistoryRepository.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.UserLoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface UserLoginHistoryRepository extends JpaRepository<UserLoginHistory, Long> {
    Page<UserLoginHistory> findByUser_UserId(Long userId, Pageable pageable);

    // Lấy toàn bộ lịch sử trong khoảng thời gian (Dành cho Admin lọc ngày)
    Page<UserLoginHistory> findByLoginTimeBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Lấy lịch sử của 1 user trong khoảng thời gian (Dành cho Admin/User lọc ngày)
    Page<UserLoginHistory> findByUser_UserIdAndLoginTimeBetween(Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable);
}