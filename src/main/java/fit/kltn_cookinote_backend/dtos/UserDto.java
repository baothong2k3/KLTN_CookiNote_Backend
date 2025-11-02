package fit.kltn_cookinote_backend.dtos;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserDto(
        Long userId,
        String username,
        String email,
        String avatarUrl,
        String displayName,
        String role,
        boolean enabled,
        boolean emailVerified,
        LocalDateTime createdAt,
        Integer recipeCount,
        Integer favoriteCount
) {
}