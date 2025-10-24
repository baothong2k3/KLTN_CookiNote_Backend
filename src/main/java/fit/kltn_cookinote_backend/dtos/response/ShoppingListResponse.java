package fit.kltn_cookinote_backend.dtos.response;

import lombok.Builder;

@Builder
public record ShoppingListResponse(
        Long id,
        Long recipeId,
        String ingredient,
        String quantity,
        Boolean checked,
        Boolean isFromRecipe
) {
}