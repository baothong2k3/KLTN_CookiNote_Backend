/*
 * @ (#) RecipeSuggestionResponse.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

public record RecipeSuggestionResponse(
        RecipeCardResponse recipe,
        double mainIngredientMatchScore,
        double overallMatchScore,
        String justification
) {
}
