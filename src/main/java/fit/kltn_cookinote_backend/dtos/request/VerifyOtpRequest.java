package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Địa chỉ email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,
        @NotBlank(message = "OTP không được để trống")
        String otp) {
}
