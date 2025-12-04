/*
 * @ (#) UpdateNutritionRequest.java    1.0    04/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 04/12/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.Positive;

public record UpdateNutritionRequest(
        @Positive(message = "Số calo phải là số dương")
        Integer calories,

        @Positive(message = "Khẩu phần phải là số dương")
        Integer servings
) {
}
