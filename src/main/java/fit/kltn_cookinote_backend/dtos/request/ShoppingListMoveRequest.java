package fit.kltn_cookinote_backend.dtos.request;

import jakarta.annotation.Nullable;

public record ShoppingListMoveRequest(
        @Nullable Long recipeId
) {
}
