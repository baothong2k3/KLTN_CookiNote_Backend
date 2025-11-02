/*
 * @ (#) UserDetailDto.java    1.0    15/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 15/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.enums.Role;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserDetailDto(
        Long userId,
        String username,
        String email,
        LocalDateTime passwordChangedAt,
        String displayName,
        String avatarUrl,
        LocalDateTime createdAt,
        Role role,
        AuthProvider authProvider,
        Integer recipeCount,
        Integer favoriteCount,
        boolean emailVerified,
        boolean enabled
) {
}
