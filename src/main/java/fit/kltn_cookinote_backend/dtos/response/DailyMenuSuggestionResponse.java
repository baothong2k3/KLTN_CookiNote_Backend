/*
 * @ (#) DailyMenuSuggestionResponse.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.DailyMenuStrategy;
import fit.kltn_cookinote_backend.enums.MealType;

import java.util.List;

public record DailyMenuSuggestionResponse(
        RecipeCardResponse recipe,
        MealType mealType,
        double score,
        List<DailyMenuStrategy> strategies,
        List<String> justifications
) {
}
