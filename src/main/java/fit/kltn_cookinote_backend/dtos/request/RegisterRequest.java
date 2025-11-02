package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Địa chỉ email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @NotBlank(message = "Tên đăng nhập không được để trống")
        @Size(min = 3, max = 100, message = "Tên đăng nhập phải từ 3 đến 100 ký tự")
        @Pattern(
                regexp = "^[a-zA-Z0-9]+$", // Chỉ chứa chữ cái (hoa/thường) và số
                message = "Tên đăng nhập chỉ được chứa chữ cái và số"
        )
        String username,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 128, message = "Mật khẩu phải từ 6 đến 128 ký tự")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,128}$",
                message = "Mật khẩu phải bao gồm cả chữ cái và số"
        )
        String password,

        @NotBlank(message = "Tên hiển thị không được để trống")
        @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
        String displayName
) {
}
