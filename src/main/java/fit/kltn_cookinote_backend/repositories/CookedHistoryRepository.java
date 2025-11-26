/*
 * @ (#) CookedHistoryRepository.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.CookedHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.List;

public interface CookedHistoryRepository extends JpaRepository<CookedHistory, Long> {

    // Tìm tất cả lịch sử nấu ăn của một user, sắp xếp mới nhất trước
    List<CookedHistory> findByUser_UserIdOrderByCookedAtDesc(Long userId);

    // Cập nhật cờ isRecipeDeleted cho tất cả entry liên quan đến recipeId
    @Modifying
    @Transactional
    @Query("UPDATE CookedHistory ch SET ch.isRecipeDeleted = true, ch.originalRecipeTitle = :recipeTitle WHERE ch.recipe.id = :recipeId")
    void markAsDeletedByRecipeId(@Param("recipeId") Long recipeId, @Param("recipeTitle") String recipeTitle);

    List<CookedHistory> findByRecipe_Id(Long recipeId);

    @Query("""
            SELECT ch.recipe.id
            FROM CookedHistory ch
            WHERE ch.user.userId = :userId
              AND ch.recipe IS NOT NULL
              AND ch.cookedAt >= :cutoff
            """)
    List<Long> findRecentRecipeIds(@Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE CookedHistory ch SET ch.isRecipeDeleted = false WHERE ch.recipe.id = :recipeId")
    void restoreByRecipeId(@Param("recipeId") Long recipeId);
}
