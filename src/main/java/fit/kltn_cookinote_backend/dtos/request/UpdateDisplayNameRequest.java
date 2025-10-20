package fit.kltn_cookinote_backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(
        @NotBlank(message = "displayName không được để trống")
        @Size(max = 100, message = "displayName tối đa 100 ký tự")
        String displayName) {
}
