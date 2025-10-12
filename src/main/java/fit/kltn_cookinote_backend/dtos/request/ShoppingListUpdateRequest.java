package fit.kltn_cookinote_backend.dtos.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

public record ShoppingListUpdateRequest(
        // null = không đổi; nếu có thì tối đa 100 ký tự
        @Nullable @Size(max = 100, message = "Tên nguyên liệu tối đa 100 ký tự.")
        String ingredient,

        // null = không đổi; nếu có thì tối đa 50 ký tự
        @Nullable @Size(max = 50, message = "Số lượng tối đa 50 ký tự.")
        String quantity,

        // null = không đổi; nếu có thì set theo giá trị
        @Nullable Boolean checked
) {
}
