/*
 * @ (#) ShoppingListRepository.java    1.0    27/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {
    List<ShoppingList> findByUser_UserIdAndRecipe_Id(Long userId, Long recipeId);

    Optional<ShoppingList> findByUser_UserIdAndRecipe_IdAndIngredientIgnoreCase(
            Long userId, Long recipeId, String ingredient
    );
}