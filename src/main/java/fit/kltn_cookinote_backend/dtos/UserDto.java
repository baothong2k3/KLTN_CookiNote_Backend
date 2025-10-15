package fit.kltn_cookinote_backend.dtos;

import lombok.Builder;

@Builder
public record UserDto(
        Long userId,
        String username,
        String email,
        String avatarUrl,
        String displayName,
        String role
) {
}