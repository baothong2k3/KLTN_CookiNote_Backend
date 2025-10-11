/*
 * @ (#) FavoriteService.java    1.0    12/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 12/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.RecipeCardResponse;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public interface FavoriteService {
    /**
     * Thêm một công thức vào danh sách yêu thích của người dùng.
     */
    void addRecipeToFavorites(Long userId, Long recipeId);

    /**
     * Lấy danh sách các công thức yêu thích của người dùng.
     */
    List<RecipeCardResponse> getFavoriteRecipes(Long userId);
}