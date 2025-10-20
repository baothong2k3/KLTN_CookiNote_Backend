package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailChangeRequest(
        @NotBlank @Email String newEmail,
        @NotBlank @Size(min = 6, max = 6) String otp
) {
}