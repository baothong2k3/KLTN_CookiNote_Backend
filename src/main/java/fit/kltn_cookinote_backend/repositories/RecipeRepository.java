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
import fit.kltn_cookinote_backend.enums.Privacy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    @Query("select r.user.userId from Recipe r where r.id = :recipeId")
    Long findOwnerId(@Param("recipeId") Long recipeId);

    boolean existsByIdAndUser_UserId(Long recipeId, Long userId);

    List<Recipe> findByPrivacyOrderByCreatedAtDesc(Privacy privacy);

    List<Recipe> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
               select r
               from Recipe r
               left join fetch r.user
               left join fetch r.category
               where r.id = :id
            """)
    Optional<Recipe> findDetailById(@Param("id") Long id);

    @Modifying
    @Query("update Recipe r set r.view = coalesce(r.view,0) + 1 where r.id = :id")
    void incrementViewById(@Param("id") Long id);

    Page<Recipe> findByCategory_IdAndPrivacy(Long categoryId, Privacy privacy, Pageable pageable);
}