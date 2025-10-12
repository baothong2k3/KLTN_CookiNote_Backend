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

public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.FavoriteId> {
    /**
     * Lấy danh sách các công thức (Recipe) mà một người dùng đã yêu thích.
     *
     * @param userId ID của người dùng.
     * @return Danh sách các đối tượng Recipe.
     */
    @Query("SELECT f.recipe FROM Favorite f WHERE f.user.userId = :userId")
    List<Recipe> findFavoriteRecipesByUserId(@Param("userId") Long userId);
}
