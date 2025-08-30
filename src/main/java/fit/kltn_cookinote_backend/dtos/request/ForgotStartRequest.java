/*
 * @ (#) ForgotStartReq.java    1.0    30/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/08/2025
 * @version: 1.0
 */

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotStartRequest(
        @NotBlank String username,
        @NotBlank @Email String email
) {
}
