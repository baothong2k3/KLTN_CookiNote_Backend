package fit.kltn_cookinote_backend.dtos;

import lombok.Builder;

@Builder
public record UserDto(
        Long userId,
        String email,
        String displayName,
        String role) {
}
