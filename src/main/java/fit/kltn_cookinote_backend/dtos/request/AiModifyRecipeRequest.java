/*
 * @ (#) AiModifyRecipeRequest.java    1.0    06/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 06/12/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;

public record AiModifyRecipeRequest(
        @NotBlank(message = "Yêu cầu sửa đổi không được để trống")
        String modificationRequest // Ví dụ: "Chuyển thành món chay", "Tăng khẩu phần lên 4 người"
) {
}
