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
import java.util.Map;
import java.util.stream.Collectors;

@Builder
public record PagedUserResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        GroupedUsers items
) {
    @Builder
    public record GroupedUsers(List<UserDto> admins, List<UserDto> users) {
    }

    public static PagedUserResponse from(Page<UserDto> userPage) {
        Map<String, List<UserDto>> groupedByRole = userPage.getContent().stream()
                .collect(Collectors.groupingBy(UserDto::role));

        GroupedUsers groupedUsers = GroupedUsers.builder()
                .admins(groupedByRole.get("ADMIN"))
                .users(groupedByRole.get("USER"))
                .build();

        return PagedUserResponse.builder()
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .hasNext(userPage.hasNext())
                .items(groupedUsers)
                .build();
    }
}
