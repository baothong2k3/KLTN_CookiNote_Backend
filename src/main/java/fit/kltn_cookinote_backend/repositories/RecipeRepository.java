/*
 * @ (#) RecipeRepository.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    @Query("select r.user.userId from Recipe r where r.id = :recipeId")
    Long findOwnerId(@Param("recipeId") Long recipeId);
}
