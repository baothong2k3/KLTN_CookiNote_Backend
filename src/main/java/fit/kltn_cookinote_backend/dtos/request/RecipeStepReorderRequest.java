/*
 * @ (#) RecipeStepReorderRequest.java    1.0    24/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 24/10/2025
 * @version: 1.0
 */

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * DTO chứa danh sách các bước cần sắp xếp lại thứ tự.
 * Danh sách này PHẢI chứa TẤT CẢ các bước hiện có của recipe.
 * Các stepNo mới PHẢI là duy nhất và bắt đầu từ 1.
 */
public record RecipeStepReorderRequest(
        @NotEmpty(message = "Danh sách thứ tự các bước không được rỗng.")
        @Valid
        List<StepOrder> steps
) {
    /**
     * Đại diện cho thứ tự mới của một bước.
     */
    public record StepOrder(
            @NotNull(message = "stepId không được null.")
            Long stepId,

            @NotNull(message = "newStepNo không được null.")
            @Positive(message = "newStepNo phải là số dương.")
            Integer newStepNo
    ) {
    }
}
