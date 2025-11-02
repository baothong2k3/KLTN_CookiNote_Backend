/*
 * @ (#) UserStatsResponse.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import lombok.Builder;

@Builder
public record UserStatsResponse(
        long totalUsers,
        long totalRecipes,
        long activeUsers,
        long newUsersToday
) {
}