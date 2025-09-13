/*
 * @ (#) PageResult.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import lombok.Builder;

import java.util.List;

@Builder
public record PageResult<T>(
        int page,          // số trang hiện tại (0-based)
        int size,          // kích thước mỗi trang
        long totalElements,
        int totalPages,
        boolean hasNext,
        List<T> items
) {
    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> p) {
        return new PageResult<>(
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.hasNext(),
                p.getContent()
        );
    }
}
