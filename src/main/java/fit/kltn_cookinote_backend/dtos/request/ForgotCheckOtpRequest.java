/*
 * @ (#) ForgotCheckOtpRequest.java    1.0    15/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 15/10/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotCheckOtpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String otp
) {
}
