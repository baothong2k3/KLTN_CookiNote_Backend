/*
 * @ (#) ResetTokenResponse.java    1.0    30/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/08/2025
 * @version: 1.0
 */

public record ResetTokenResponse(String resetToken, long expiresInSeconds) {
}
