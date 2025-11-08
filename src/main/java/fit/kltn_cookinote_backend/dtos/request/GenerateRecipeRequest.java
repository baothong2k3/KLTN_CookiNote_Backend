/*
 * @ (#) GenerateRecipeRequest.java    1.0    08/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 08/11/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO để yêu cầu AI tạo công thức từ một tên món ăn.
 */
public record GenerateRecipeRequest(
        @NotBlank(message = "Tên món ăn không được để trống")
        @Size(max = 255, message = "Tên món ăn quá dài")
        String dishName
) {
}
