package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecipeStepCreate(
        @Positive Integer stepNo,
        @NotBlank @Size(max = 4096) String content
) {
}
