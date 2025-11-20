/*
 * @ (#) ImportRecipeRequest.java    1.0    16/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 16/11/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO để nhận yêu cầu nhập công thức từ URL.
 */
public record ImportRecipeRequest(
        @NotBlank(message = "URL không được để trống")
        @Size(max = 2048, message = "URL quá dài")
        String url
) {
}
