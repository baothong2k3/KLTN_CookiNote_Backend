/*
 * @ (#) ShareRepository.java    1.0    28/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {
    // Tìm share record dựa trên shareCode
    Optional<Share> findByShareCode(String shareCode);

    // Lấy Recipe ID từ share code (tối ưu hơn là lấy cả entity Share rồi lấy recipe)
    @Query("SELECT s.recipe.id FROM Share s WHERE s.shareCode = :shareCode")
    Optional<Long> findRecipeIdByShareCode(@Param("shareCode") String shareCode);
}
