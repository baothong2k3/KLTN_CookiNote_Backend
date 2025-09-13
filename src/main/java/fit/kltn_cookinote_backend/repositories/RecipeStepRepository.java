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

import fit.kltn_cookinote_backend.entities.RecipeStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Long> {
    @Query("""
            select r.user.userId
            from RecipeStep s join s.recipe r
            where s.id = :stepId
            """)
    Long findOwnerIdByStepId(@Param("stepId") Long stepId);
}
