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

import fit.kltn_cookinote_backend.dtos.request.RegisterRequest;
import fit.kltn_cookinote_backend.dtos.request.ResendOtpRequest;
import fit.kltn_cookinote_backend.dtos.request.VerifyOtpRequest;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.AuthService;
import fit.kltn_cookinote_backend.services.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final OtpService otpService;

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
}
