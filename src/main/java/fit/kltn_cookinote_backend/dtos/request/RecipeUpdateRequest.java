package fit.kltn_cookinote_backend.dtos.request;

import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.enums.Privacy;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecipeUpdateRequest(
        @NotNull Long categoryId,
        @NotNull String title,
        String description,
        Integer prepareTime,
        Integer cookTime,
        Difficulty difficulty,
        Privacy privacy, // nếu null sẽ giữ nguyên privacy cũ (không tự động chuyển)
        List<RecipeIngredientCreate> ingredients, // thay thế toàn bộ
        List<RecipeStepCreate> steps
) {
}
