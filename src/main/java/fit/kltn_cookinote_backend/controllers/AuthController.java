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

import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.LoginResponse;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.services.AuthService;
import fit.kltn_cookinote_backend.services.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final LoginService loginService;

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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid LoginRequest req,
                                                            HttpServletRequest httpReq) {
        var resp = loginService.login(req);
        return ResponseEntity.ok(
                ApiResponse.success("Đăng nhập thành công.", resp, httpReq.getRequestURI())
        );
    }

    // Làm mới access token bằng refresh token
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenPair>> refresh(@RequestBody @Valid RefreshRequest req,
                                                          HttpServletRequest httpReq) {
        var tokens = loginService.refresh(req);
        return ResponseEntity.ok(
                ApiResponse.success("Làm mới token thành công.", tokens, httpReq.getRequestURI())
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody RefreshRequest req,
                                                    @RequestHeader(name = "Authorization") String auth,
                                                    HttpServletRequest httpReq) {
        String currentAccess = null;
        if (auth != null && auth.startsWith("Bearer ")) currentAccess = auth.substring(7);

        loginService.logout(req.refreshToken(), currentAccess);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công.", httpReq.getRequestURI()));
    }
}
