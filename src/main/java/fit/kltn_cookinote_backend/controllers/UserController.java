/*
 * @ (#) UserController.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.mappers.UserMapper;
import fit.kltn_cookinote_backend.services.CloudinaryService;
import fit.kltn_cookinote_backend.services.EmailChangeService;
import fit.kltn_cookinote_backend.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final EmailChangeService emailChangeService;
    private final CloudinaryService cloudinaryService;
    private final UserMapper userMapper;

    // Kiểm tra access token
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(@AuthenticationPrincipal User user,
                                                               HttpServletRequest httpReq) {
        UserDto data = userMapper.toDto(user);
        return ResponseEntity.ok(ApiResponse.success("OK", data, httpReq.getRequestURI()));
    }

    /**
     * Lấy toàn bộ thông tin chi tiết của người dùng hiện tại.
     */
    @GetMapping("/me/details")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserDetailDto>> getMyDetails(@AuthenticationPrincipal User authUser, HttpServletRequest httpReq) {
        UserDetailDto userDetails = userService.getUserDetails(authUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết thành công", userDetails, httpReq.getRequestURI()));
    }

    @PatchMapping("/display-name")
    public ResponseEntity<ApiResponse<UserDto>> updateDisplayName(
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody UpdateDisplayNameRequest req,
            HttpServletRequest httpReq) {

        if (authUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Token đã hết hạn hoặc không hợp lệ", httpReq.getRequestURI()));
        }

        UserDto dto = userService.updateDisplayName(authUser.getUserId(), req);
        return ResponseEntity.ok(
                ApiResponse.success("Cập nhật displayName thành công.", dto, httpReq.getRequestURI())
        );
    }

    @PostMapping("/email-change-request")
    public ResponseEntity<ApiResponse<OtpRateInfo>> changeRequest(@AuthenticationPrincipal User authUser,
                                                                  @Valid @RequestBody ChangeEmailRequest req,
                                                                  HttpServletRequest httpReq) {
        if (authUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Token đã hết hạn hoặc không hợp lệ", httpReq.getRequestURI()));
        }
        OtpRateInfo info = emailChangeService.requestChange(authUser, req);
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(info.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(info.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(info.resetAfter()))
                .body(ApiResponse.success("Đã gửi OTP đến email mới.", info, httpReq.getRequestURI()));
    }

    @PostMapping("/email-resend-otp")
    public ResponseEntity<ApiResponse<OtpRateInfo>> resendOtp(@AuthenticationPrincipal User authUser,
                                                              @Valid @RequestBody ChangeEmailRequest req,
                                                              HttpServletRequest httpReq) {
        if (authUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Token đã hết hạn hoặc không hợp lệ", httpReq.getRequestURI()));
        }
        OtpRateInfo info = emailChangeService.resendOtp(authUser, req);
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(info.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(info.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(info.resetAfter()))
                .body(ApiResponse.success("Đã gửi lại OTP.", info, httpReq.getRequestURI()));
    }

    @PostMapping("/email-verify-change")
    public ResponseEntity<ApiResponse<Void>> verifyChange(@AuthenticationPrincipal User authUser,
                                                          @Valid @RequestBody VerifyEmailChangeRequest req,
                                                          HttpServletRequest httpReq) {
        if (authUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Token đã hết hạn hoặc không hợp lệ", httpReq.getRequestURI()));
        }
        emailChangeService.verifyAndCommit(authUser, req);
        return ResponseEntity.ok(ApiResponse.success("Đổi email thành công.", httpReq.getRequestURI()));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal User authUser, @Valid @RequestBody ChangePasswordRequest req, HttpServletRequest httpReq) {
        if (authUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Token đã hết hạn hoặc không hợp lệ", httpReq.getRequestURI()));
        }
        userService.changePassword(authUser.getUserId(), req.currentPassword(), req.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công.", httpReq.getRequestURI()));
    }

    @PutMapping(value = "/avatar", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<String>> updateAvatar(
            @AuthenticationPrincipal User authUser,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest req
    ) throws Exception {
        String url = cloudinaryService.updateAvatar(authUser.getUserId(), file);
        return ResponseEntity.ok(
                ApiResponse.success("Cập nhật avatar thành công", url, req.getRequestURI())
        );
    }

    @GetMapping("/check-password")
    public ResponseEntity<ApiResponse<Boolean>> checkPassword(@AuthenticationPrincipal User authUser,
                                                              @RequestParam("currentPassword") String currentPassword,
                                                              HttpServletRequest httpReq) {
        if (authUser == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "Token đã hết hạn hoặc không hợp lệ", httpReq.getRequestURI()));
        }
        boolean matches = userService.checkPassword(authUser.getUserId(), currentPassword);
        return ResponseEntity.ok(
                ApiResponse.success("OK", matches, httpReq.getRequestURI())
        );
    }
}
