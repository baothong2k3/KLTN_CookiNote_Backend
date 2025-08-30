package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ForgotResetRequest(
        @NotBlank String resetToken,
        @NotBlank
        @Size(min = 6, max = 128)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,128}$",
                message = "Mật khẩu phải dài từ 6-128 ký tự và bao gồm cả chữ cái và số"
        )
        String newPassword
) {
}
