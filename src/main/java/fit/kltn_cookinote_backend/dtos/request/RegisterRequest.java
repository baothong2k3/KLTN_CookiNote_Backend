package fit.kltn_cookinote_backend.dtos.request;

public record RegisterRequest(String email, String username, String password, String displayName) {
}
