package fit.kltn_cookinote_backend.dtos;

public record GoogleProfile(
        String sub,            // google user id
        String email,
        boolean emailVerified,
        String name,
        String picture
) {
}
