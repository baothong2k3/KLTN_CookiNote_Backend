/*
 * @ (#) DailyMenuResponse.java    1.0    09/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 09/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.MealType;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public record DailyMenuResponse(
        DailyMenuRecipeCardResponse anchorRecipe,
        MealType anchorMealType,
        String anchorSource,
        String favoriteCategoryName,
        int freshnessWindowDays,
        LocalDate generatedDate,
        List<DailyMenuSuggestionResponse> suggestions
) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;
}