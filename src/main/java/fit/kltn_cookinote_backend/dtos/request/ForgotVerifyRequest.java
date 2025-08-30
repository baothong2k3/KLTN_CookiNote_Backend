package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ForgotVerifyRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String otp,
        @NotBlank
        @Size(min = 6, max = 128)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,128}$",
                message = "Mật khẩu phải dài từ 6-128 ký tự và bao gồm cả chữ cái và số"
        )
        String newPassword
) {
}