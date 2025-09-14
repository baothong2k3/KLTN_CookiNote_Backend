package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipeIngredientCreate(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String quantity
) {
}
