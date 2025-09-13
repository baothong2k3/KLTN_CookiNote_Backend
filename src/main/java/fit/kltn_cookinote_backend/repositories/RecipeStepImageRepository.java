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

public interface RecipeStepImageRepository extends JpaRepository<RecipeStepImage, Long> {
    long countByStep_Id(Long stepId);
}
