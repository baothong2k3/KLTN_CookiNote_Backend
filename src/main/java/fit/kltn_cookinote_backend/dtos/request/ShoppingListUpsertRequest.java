package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record ShoppingListUpsertRequest(
        @NotBlank String ingredient,
        String quantity
) {
}
