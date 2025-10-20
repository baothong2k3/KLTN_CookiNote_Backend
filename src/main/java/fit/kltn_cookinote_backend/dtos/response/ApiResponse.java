package fit.kltn_cookinote_backend.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final int code;        // 200, 400, 401, 404, 500...
    private final String message;  // "OK", "Đăng ký thành công", ...
    private final T data;          // payload (nếu có)
    private final Instant timestamp;
    private final String path;

    public static <T> ApiResponse<T> success(String message, T data, String path) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    public static ApiResponse<Void> success(String message, String path) {
        return success(message, null, path);
    }

    public static <T> ApiResponse<T> error(int code, String message, String path) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }
}