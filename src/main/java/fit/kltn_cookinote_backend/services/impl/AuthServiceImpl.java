/*
 * @ (#) AuthServiceImpl.java    1.0    26/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 26/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.enums.OtpPurpose;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.AuthService;
import fit.kltn_cookinote_backend.services.OtpService;
import fit.kltn_cookinote_backend.services.SessionAllowlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final OtpService otpService;
    private final SessionAllowlistService sessionAllowlistService;

    @Override
    public void register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email đã tồn tại.");
        } else if (userRepo.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        }

        User user = User.builder()
                .email(req.email())
                .username(req.username())
                .displayName(req.displayName())
                .password(encoder.encode(req.password()))
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .enabled(false)
                .build();

        userRepo.save(user);
        otpService.createAndSendEmailVerifyOtp(user);
    }

    @Override
    public void verifyEmail(VerifyOtpRequest req) {
        User user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
        otpService.verifyEmailOtp(user, req.otp());
    }

    @Override
    public OtpRateInfo resendOtp(ResendOtpRequest req) {
        User user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email đã xác thực.");
        }
        return otpService.resendEmailVerifyOtp(user);
    }

    @Override
    public OtpRateInfo startForgotPassword(ForgotStartRequest req) {
        Optional<User> ou = userRepo.findByEmail(req.email());

        if (ou.isEmpty()) {
            // Trả OtpRateInfo “mask” (toàn số 0) — không tiết lộ user tồn tại hay không
            return OtpRateInfo.zero();
        }

        User user = ou.get();

        // Nếu là tài khoản Google → không gửi OTP, vẫn trả về thông điệp chung
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            return OtpRateInfo.zero();
        }
        return otpService.createAndSendEmailResetPassword(user);
    }

    @Override
    public void resetPasswordWithOtp(ForgotVerifyRequest req) {
        final String username = req.username().trim();
        final String email = req.email().trim();

        User user = userRepo.findByUsernameAndEmail(username, email)
                .orElseThrow(() -> new IllegalStateException("OTP không hợp lệ hoặc đã hết hạn"));

        // chặn tài khoản Google
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new IllegalStateException("Tài khoản này đăng nhập bằng Google. Vui lòng đăng nhập bằng Google.");
        }
        // verify + consume OTP (one-time) (đảm bảo @Transactional để tránh race)
        otpService.verifyAndConsumeOtpOrThrow(user, OtpPurpose.PASSWORD_RESET, req.otp());

        // đổi mật khẩu
        user.setPassword(encoder.encode(req.newPassword()));
        userRepo.save(user);

        // revoke toàn bộ phiên/refresh tokens
        sessionAllowlistService.revokeAllForUser(user.getUserId());
    }
}