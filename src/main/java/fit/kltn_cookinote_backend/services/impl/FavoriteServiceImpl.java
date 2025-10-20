/*
 * @ (#) FavoriteServiceImpl.java    1.0    12/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 12/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.RecipeCardResponse;
import fit.kltn_cookinote_backend.entities.Favorite;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.FavoriteRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.FavoriteService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

    @Override
    @Transactional
    public void addRecipeToFavorites(Long userId, Long recipeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy công thức với id: " + recipeId));

        if (recipe.getPrivacy() == Privacy.PRIVATE && !recipe.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này.");
        }

        Favorite favorite = Favorite.of(user, recipe);
        favoriteRepository.save(favorite);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeCardResponse> getFavoriteRecipes(Long userId) {
        List<Recipe> favoriteRecipes = favoriteRepository.findFavoriteRecipesByUserId(userId);
        return favoriteRecipes.stream()
                .map(RecipeCardResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeRecipeFromFavorites(Long userId, Long recipeId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
        recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy công thức với id: " + recipeId));

        Favorite.FavoriteId favoriteId = new Favorite.FavoriteId(userId, recipeId);
        favoriteRepository.deleteById(favoriteId);
    }
}