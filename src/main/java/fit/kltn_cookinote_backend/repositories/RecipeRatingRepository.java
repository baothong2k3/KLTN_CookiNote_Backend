/*
 * @ (#) RecipeRatingRepository.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeRatingRepository extends JpaRepository<RecipeRating, Long> {
    Optional<RecipeRating> findByUser_UserIdAndRecipe_Id(Long userId, Long recipeId);

    List<RecipeRating> findByRecipe_Id(Long recipeId);

    // Query để tính tổng điểm và số lượt rating cho một recipe
    @Query("SELECT SUM(r.score), COUNT(r) FROM RecipeRating r WHERE r.recipe.id = :recipeId")
    Optional<Object[]> calculateRatingStats(@Param("recipeId") Long recipeId);
}
