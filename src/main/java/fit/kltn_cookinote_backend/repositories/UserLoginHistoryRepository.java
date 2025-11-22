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

import java.util.List;

public interface UserLoginHistoryRepository extends JpaRepository<UserLoginHistory, Long> {
    List<UserLoginHistory> findByUser_UserIdOrderByLoginTimeDesc(Long userId);

    Page<UserLoginHistory> findByUser_UserId(Long userId, Pageable pageable);
}