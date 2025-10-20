/*
 * @ (#) RecipeCoverImageHistoryRepository.java    1.0    11/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeCoverImageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeCoverImageHistoryRepository extends JpaRepository<RecipeCoverImageHistory, Long> {
    List<RecipeCoverImageHistory> findByRecipe_Id(Long recipeId);

    @Modifying
    @Query("UPDATE RecipeCoverImageHistory h SET h.active = false WHERE h.recipe.id = :recipeId")
    void deactivateAllByRecipeId(@Param("recipeId") Long recipeId);
}
