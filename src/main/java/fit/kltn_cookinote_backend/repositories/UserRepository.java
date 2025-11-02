/*
 * @ (#) UserRepository.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findByUsernameAndEmail(String username, String email);

    /**
     * Đếm số lượng người dùng đang ở trạng thái enabled = true.
     */
    Long countByEnabledTrue();

    /**
     * Đếm số lượng người dùng mới được tạo trong ngày hôm nay (dựa trên múi giờ của DB).
     */
    @Query("SELECT COUNT(u) FROM User u WHERE DATE(u.createdAt) = CURRENT_DATE")
    Long countNewUsersToday();
}
