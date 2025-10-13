/*
 * @ (#) LoginResponse.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.TokenPair;

@lombok.Builder
public record LoginResponse(Long userId, String email, String avatarUrl, String displayName, TokenPair tokens) {}
