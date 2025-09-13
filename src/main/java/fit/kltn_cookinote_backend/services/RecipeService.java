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
import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;

public interface RecipeService {
    RecipeResponse createByRecipe(Long id, RecipeCreateRequest req);
    RecipeResponse getDetail(Long viewerUserIdOrNull, Long recipeId);
}
