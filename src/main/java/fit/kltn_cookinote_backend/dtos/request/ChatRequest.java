/*
 * @ (#) ChatRequest.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "Câu hỏi không được để trống")
        String message
) {
}
