package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RecipeStepCreate(
        @Positive Integer stepNo,

        @NotBlank
        @Size(max = 4096)
        String content,

        @PositiveOrZero(message = "Thời gian gợi ý phải là số dương hoặc bằng 0")
        Integer suggestedTime,

        @Size(max = 2048, message = "Tips tối đa 2048 ký tự")
        String tips
) {
}
