/*
 * @ (#) RecipeService.java    1.0    11/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RecipeCreateRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.*;

import java.util.List;

public interface RecipeService {
    RecipeResponse createByRecipe(Long id, RecipeCreateRequest req);

    RecipeResponse getDetail(Long viewerUserIdOrNull, Long recipeId);

    PageResult<RecipeCardResponse> listPublicByCategory(Long categoryId, int page, int size);

    PageResult<RecipeCardResponse> listPublic(int page, int size);

    PageResult<RecipeCardResponse> listByOwner(Long ownerUserId, Long viewerUserIdOrNull, int page, int size);

    List<RecipeStepItem> getSteps(Long viewerUserIdOrNull, Long recipeId);

    List<RecipeIngredientItem> getIngredients(Long viewerUserIdOrNull, Long recipeId);

    RecipeResponse updateContent(Long actorUserId, Long recipeId, RecipeUpdateRequest req);

    void deleteRecipe(Long actorUserId, Long recipeId);
}
