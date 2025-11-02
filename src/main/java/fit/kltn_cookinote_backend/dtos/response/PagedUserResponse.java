/*
 * @ (#) PagedUserResponse.java    1.0    15/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 15/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;

@Builder
public record PagedUserResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        List<UserDto> items
) {

    public static PagedUserResponse from(Page<UserDto> userPage) {

        return PagedUserResponse.builder()
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .hasNext(userPage.hasNext())
                .items(userPage.getContent())
                .build();
    }
}
