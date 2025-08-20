/*
 * @ (#) AuthController.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.OtpRateInfo;
import fit.kltn_cookinote_backend.dtos.request.RegisterRequest;
import fit.kltn_cookinote_backend.dtos.request.ResendOtpRequest;
import fit.kltn_cookinote_backend.dtos.request.VerifyOtpRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody @Valid RegisterRequest req,
                                                      HttpServletRequest httpReq) {
        authService.register(req);
        return ResponseEntity.ok(
                ApiResponse.success("Đăng ký thành công. Vui lòng kiểm tra email để nhập OTP.",
                        httpReq.getRequestURI())
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestBody @Valid VerifyOtpRequest req,
                                                    HttpServletRequest httpReq) {
        authService.verifyEmail(req);
        return ResponseEntity.ok(
                ApiResponse.success("Xác thực email thành công. Tài khoản đã được kích hoạt.",
                        httpReq.getRequestURI())
        );
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<OtpRateInfo>> resend(@RequestBody @Valid ResendOtpRequest req,
                                                           HttpServletRequest httpReq) {
        OtpRateInfo info = authService.resendOtp(req);
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(info.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(info.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(info.resetAfter()))
                .body(ApiResponse.success("Đã gửi lại OTP.", info, httpReq.getRequestURI()));
    }
}
