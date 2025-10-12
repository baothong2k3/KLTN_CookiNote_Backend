/*
 * @ (#) FavoriteRepository.java    1.0    12/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 12/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.Favorite;
import fit.kltn_cookinote_backend.entities.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Optional<Favorite> findByUser_UserIdAndRecipe_Id(Long userId, Long recipeId);

    List<Favorite> findByRecipe_Id(Long recipeId);

    List<Favorite> findByUser_UserIdOrderByIdDesc(Long userId);
}