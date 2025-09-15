/*
 * @ (#) CategoryResponse.java    1.0    07/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 07/09/2025
 * @version: 1.0
 */

import lombok.Builder;

@Builder
public record CategoryResponse(
        Long id,
        String name,
        String description,
        String imageUrl
) {
}
