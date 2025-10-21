package fit.kltn_cookinote_backend.dtos.request;

import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.enums.Privacy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record RecipeCreateRequest(
        @NotNull(message = "categoryId bắt buộc")
        Long categoryId,

        @NotBlank @Size(max = 255)
        String title,

        @Size(max = 2048)
        String description,

        @PositiveOrZero
        Integer prepareTime,

        @PositiveOrZero
        Integer cookTime,

        Difficulty difficulty,

        Privacy privacy,

        @NotEmpty(message = "Danh sách nguyên liệu không được rỗng")
        @Valid
        List<RecipeIngredientCreate> ingredients,

        @NotEmpty(message = "Danh sách bước nấu không được rỗng")
        @Valid
        List<RecipeStepCreate> steps
) {
}
