/*
 * @ (#) EmailChangeServiceImpl.java    1.0    26/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 26/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.ChangeEmailRequest;
import fit.kltn_cookinote_backend.dtos.request.VerifyEmailChangeRequest;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.EmailChangeService;
import fit.kltn_cookinote_backend.services.OtpService;
import fit.kltn_cookinote_backend.services.PendingEmailChangeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailChangeServiceImpl implements EmailChangeService {
    private final UserRepository userRepo;
    private final PendingEmailChangeStore store;
    private final OtpService otpService;

    private static final Duration PENDING_TTL = Duration.ofMinutes(5);

    @Override
    @Transactional
    public OtpRateInfo requestChange(User user, ChangeEmailRequest req) {
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new IllegalStateException("Tài khoản Google không thể đổi email.");
        }

        String newEmail = req.newEmail().trim().toLowerCase();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Email mới phải khác email hiện tại.");
        }
        if (userRepo.existsByEmail(newEmail)) {
            throw new IllegalArgumentException("Email này đã được sử dụng.");
        }

        // Lưu pending vào Redis để ràng buộc OTP với email MỚI
        store.put(user.getUserId(), newEmail, PENDING_TTL);

        // Gửi OTP tới email mới
        return otpService.createAndSendEmailChangeOtp(user, newEmail);
    }

    @Override
    @Transactional
    public OtpRateInfo resendOtp(User user, ChangeEmailRequest req) {
        String pending = store.get(user.getUserId());
        if (pending == null) {
            throw new IllegalStateException("Không có yêu cầu đổi email nào đang chờ xác thực.");
        }
        if (!pending.equalsIgnoreCase(req.newEmail().trim())) {
            throw new IllegalStateException("Email mới không khớp với yêu cầu đang chờ xác thực.");
        }
        return otpService.createAndSendEmailChangeOtp(user, pending);
    }

    @Override
    public void verifyAndCommit(User user, VerifyEmailChangeRequest req) {
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new IllegalStateException("Tài khoản Google không thể đổi email.");
        }
        String pending = store.get(user.getUserId());
        if (pending == null) {
            throw new IllegalStateException("Không có yêu cầu đổi email nào đang chờ xác thực.");
        }
        if (!pending.equalsIgnoreCase(req.newEmail().trim())) {
            throw new IllegalStateException("Email mới không khớp với yêu cầu đang chờ xác thực.");
        }

        // Verify OTP (purpose EMAIL_CHANGE)
        otpService.verifyEmailChangeOtp(user, req.otp());

        // Commit: cập nhật email trong DB
        user.setEmail(pending);
        user.setEmailVerified(true); // xác thực email mới
        userRepo.save(user);

        // Dọn dẹp pending
        store.delete(user.getUserId());
    }
}
