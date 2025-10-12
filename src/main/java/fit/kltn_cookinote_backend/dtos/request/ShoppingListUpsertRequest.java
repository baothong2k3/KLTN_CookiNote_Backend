package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShoppingListUpsertRequest(
        @NotBlank(message = "Tên nguyên liệu không được trống.")
        @Size(max = 100, message = "Tên nguyên liệu tối đa 100 ký tự.")
        String ingredient,

        @Size(max = 50, message = "Số lượng tối đa 50 ký tự.")
        String quantity
) {
}
