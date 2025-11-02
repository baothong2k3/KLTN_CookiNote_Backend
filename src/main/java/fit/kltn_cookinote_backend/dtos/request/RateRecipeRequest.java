/*
 * @ (#) RateRecipeRequest.java    1.0    29/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RateRecipeRequest(
        @NotNull(message = "Điểm rating không được để trống")
        @Min(value = 1, message = "Điểm rating phải từ 1 đến 5")
        @Max(value = 5, message = "Điểm rating phải từ 1 đến 5")
        Integer score // Số sao người dùng đánh giá
) {
}
