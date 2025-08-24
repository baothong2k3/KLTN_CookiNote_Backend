/*
 * @ (#) ChangePasswordRequest.java    1.0    24/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 24/08/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 128)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,128}$",
                message = "Mật khẩu phải dài từ 6-128 ký tự và bao gồm cả chữ cái và số"
        ) String newPassword
) {
}