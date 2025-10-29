/*
 * @ (#) RecipeStepImageRepository.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeStepImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeStepImageRepository extends JpaRepository<RecipeStepImage, Long> {
    long countByStep_Id(Long stepId);

    List<RecipeStepImage> findByStep_Id(Long stepId);

    @Modifying
    @Query("delete from RecipeStepImage i where i.id in :ids")
    void deleteByIdIn(Collection<Long> ids);

    @Query("SELECT i FROM RecipeStepImage i JOIN FETCH i.step s JOIN FETCH s.recipe r JOIN FETCH r.user u WHERE i.id IN :ids")
    List<RecipeStepImage> findByIdInWithStepRecipeAndUser(@Param("ids") List<Long> ids);
}
