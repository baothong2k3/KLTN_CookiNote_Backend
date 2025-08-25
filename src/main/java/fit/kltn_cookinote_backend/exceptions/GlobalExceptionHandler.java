package fit.kltn_cookinote_backend.exceptions;


import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.limiters.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.PropertyValueException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1) Lỗi ràng buộc DB (NOT NULL, Unique, Data truncated...)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest req) {

        String raw = mostSpecificMessage(ex); // lấy message sâu nhất
        String message = normalizeSqlMessage(raw); // làm sạch + map ra thông điệp thân thiện

        int code = guessStatusFromSqlMessage(raw); // 400 / 409
        return ResponseEntity.status(code)
                .body(ApiResponse.error(code, message, req.getRequestURI()));
    }

    // 2) Lỗi property null ở Hibernate trước khi xuống DB (thỉnh thoảng gặp)
    @ExceptionHandler(PropertyValueException.class)
    public ResponseEntity<ApiResponse<Void>> handlePropertyValue(
            PropertyValueException ex, HttpServletRequest req) {
        String msg = "Thuộc tính '" + ex.getPropertyName() + "' của '" + ex.getEntityName() + "' không được null.";
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, msg, req.getRequestURI()));
    }

    // 3) Transaction bọc các lỗi validation/persistence
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleTx(TransactionSystemException ex, HttpServletRequest req) {
        ConstraintViolationException cve = findCause(ex, ConstraintViolationException.class);
        if (cve != null) {
            String message = joinViolationMessages(cve.getConstraintViolations());
            return ResponseEntity.status(400)
                    .body(ApiResponse.error(400, message, req.getRequestURI()));
        }

        String raw = mostSpecificMessage(ex);
        String message = normalizeSqlMessage(raw);
        int code = guessStatusFromSqlMessage(raw);
        return ResponseEntity.status(code)
                .body(ApiResponse.error(code, message, req.getRequestURI()));
    }

    // 4) Cuối cùng: fallback chung (ít dùng hơn sau khi đã có các handler trên)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex, HttpServletRequest req) {
        String raw = mostSpecificMessage(ex);
        String message = normalizeSqlMessage(raw);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(500, message, req.getRequestURI()));
    }

    // 5) Bắt trực tiếp lỗi Bean Validation (khi gọi Validator hoặc lúc persist/flush ném thẳng ra)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {

        String message = joinViolationMessages(ex.getConstraintViolations());
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, message, req.getRequestURI()));
    }

    // ---------- Helpers ----------

    /**
     * Lấy message cụ thể nhất ở root-cause (MySQL/Driver).
     */
    private String mostSpecificMessage(Throwable ex) {
        String msg = NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
        return msg != null ? msg.trim() : "Unknown database error";
    }

    /**
     * Chuẩn hoá message: cắt bớt phần dư thừa, giữ nội dung có ích cho client.
     */
    private String normalizeSqlMessage(String msg) {
        if (msg == null) return "Database error";
        // Một vài cleanup nhẹ nhàng
        msg = msg.replaceAll("\\s+", " ");
        // Có thể thêm các map tuỳ ý:
        // - Duplicate entry 'xxx' for key 'uk_user_email' -> Email đã tồn tại.
        if (msg.startsWith("Duplicate entry")) {
            return "Dữ liệu đã tồn tại: " + msg;
        }
        if (msg.contains("cannot be null")) {
            return msg;
        }
        if (msg.contains("Data truncated")) {
            return "Dữ liệu không đúng định dạng/độ dài: " + msg;
        }
        return msg;
    }

    /**
     * Suy đoán status code phù hợp dựa trên thông báo SQL.
     */
    private int guessStatusFromSqlMessage(String msg) {
        if (msg == null) return 400;
        String m = msg.toLowerCase();

        // NOT NULL, kiểu dữ liệu: 400 Bad Request
        if (m.contains("cannot be null") || m.contains("data truncated") || m.contains("incorrect") || m.contains("invalid")) {
            return 400;
        }
        // Duplicate/unique constraint: 409 Conflict
        if (m.contains("duplicate entry") || m.contains("unique") || m.contains("duplicate")) {
            return 409;
        }
        return 400;
    }

    /**
     * Tìm cause theo kiểu cụ thể trong chuỗi nguyên nhân
     */
    private <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        Throwable cur = ex;
        while (cur != null) {
            if (type.isInstance(cur)) return type.cast(cur);
            cur = cur.getCause();
        }
        return null;
    }

    /**
     * Nối các thông điệp từ các ConstraintViolation thành chuỗi gọn gàng
     */
    private String joinViolationMessages(Set<ConstraintViolation<?>> violations) {
        if (violations == null || violations.isEmpty()) return "Dữ liệu không hợp lệ.";
        return violations.stream()
                .map(ConstraintViolation::getMessage) // chỉ lấy message, bỏ class/property dài dòng
                .distinct()
                .collect(Collectors.joining("; "));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooMany(
            TooManyRequestsException ex, HttpServletRequest req) {

        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Limit", "5")
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.error(429, ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException ex,
                                                              HttpServletRequest req) {
        HttpStatus status = HttpStatus.BAD_REQUEST; // 400
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Void>> handleIO(HttpServletRequest req) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; // 500
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), "Không thể xử lý file ảnh.", req.getRequestURI()));
    }
}