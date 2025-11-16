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
import org.hibernate.validator.constraints.URL;

/**
 * DTO để nhận yêu cầu nhập công thức từ URL.
 */
public record ImportRecipeRequest(
        @NotBlank(message = "URL không được để trống")
        @URL(message = "URL không hợp lệ")
        String url
) {
}
