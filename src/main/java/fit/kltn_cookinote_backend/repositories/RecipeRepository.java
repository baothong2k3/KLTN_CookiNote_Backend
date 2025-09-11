/*
 * @ (#) RecipeRepository.java    1.0    11/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}
