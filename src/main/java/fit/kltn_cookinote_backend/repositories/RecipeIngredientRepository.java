/*
 * @ (#) RecipeIngredientRepository.java    1.0    15/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 15/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {
    List<RecipeIngredient> findByRecipe_IdOrderByIdAsc(Long recipeId);
}
