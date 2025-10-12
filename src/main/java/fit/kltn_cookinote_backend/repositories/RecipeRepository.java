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

import java.util.Collection;
import java.util.Optional;


public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    @Query("select r.user.userId from Recipe r where r.id = :recipeId AND r.deleted = false")
    Long findOwnerId(@Param("recipeId") Long recipeId);

    @Query("""
               select r
               from Recipe r
               left join fetch r.user
               left join fetch r.category
               where r.id = :id AND r.deleted = false
            """)
    Optional<Recipe> findDetailById(@Param("id") Long id);

    @Modifying
    @Query("update Recipe r set r.view = coalesce(r.view,0) + 1 where r.id = :id AND r.deleted = false")
    void incrementViewById(@Param("id") Long id);

    @Query("SELECT r FROM Recipe r WHERE r.category.id = :categoryId AND r.privacy = :privacy AND r.deleted = false")
    Page<Recipe> findByCategory_IdAndPrivacy(@Param("categoryId") Long categoryId, @Param("privacy") Privacy privacy, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.privacy = :privacy AND r.deleted = false")
    Page<Recipe> findByPrivacy(@Param("privacy") Privacy privacy, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.user.userId = :ownerId AND r.deleted = false")
    Page<Recipe> findByUser_UserId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.user.userId = :ownerId AND r.privacy IN :privacies AND r.deleted = false")
    Page<Recipe> findByUser_UserIdAndPrivacyIn(@Param("ownerId") Long ownerId, @Param("privacies") Collection<Privacy> privacies, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.deleted = true AND (:userId IS NULL OR r.user.userId = :userId)")
    Page<Recipe> findDeleted(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT r FROM Recipe r WHERE r.id = :id AND r.deleted = true")
    Optional<Recipe> findDeletedById(@Param("id") Long id);
}