package fit.kltn_cookinote_backend.dtos.request;

@lombok.Builder
public record TokenPair(String accessToken, String refreshToken,
                        long accessExpiresInSeconds, long refreshExpiresInSeconds) {
}
