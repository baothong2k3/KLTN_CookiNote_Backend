/*
 * @ (#) UserServiceImpl.java    1.0    26/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 26/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.UserDto;
import fit.kltn_cookinote_backend.dtos.request.ForgotResetRequest;
import fit.kltn_cookinote_backend.dtos.request.ForgotStartRequest;
import fit.kltn_cookinote_backend.dtos.request.ForgotVerifyRequest;
import fit.kltn_cookinote_backend.dtos.request.UpdateDisplayNameRequest;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.dtos.response.ResetTokenResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.mappers.UserMapper;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.RefreshTokenService;
import fit.kltn_cookinote_backend.services.SessionAllowlistService;
import fit.kltn_cookinote_backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepo;
    private final UserMapper userMapper;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshService;
    private final SessionAllowlistService sessionService;

    @Override
    @Transactional
    public UserDto updateDisplayName(Long userId, UpdateDisplayNameRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        String newName = req.displayName() == null ? "" : req.displayName().trim();
        if (newName.isEmpty()) {
            throw new IllegalArgumentException("Tên hiển thị không được để trống.");
        }
        if (newName.length() > 100) {
            throw new IllegalArgumentException("Tên hiển thị tối đa 100 ký tự.");
        }
        if (newName.equals(user.getDisplayName())) {
            return userMapper.toDto(user);
        }

        user.setDisplayName(newName);
        userRepo.save(user);

        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        // Chỉ cho tài khoản LOCAL đổi mật khẩu nội bộ
        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new IllegalStateException("Tài khoản không hỗ trợ đổi mật khẩu nội bộ.");
        }

        // Kiểm tra mật khẩu hiện tại
        if (!encoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }

        // Không cho đặt trùng
        if (encoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ.");
        }

        // Cập nhật mật khẩu
        user.setPassword(encoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now(ZoneOffset.UTC)); // khuyến nghị
        userRepo.save(user);

        // Revoke ALL tokens của user: buộc đăng nhập lại ở mọi nơi
        refreshService.revokeAllForUser(userId);
        sessionService.revokeAllForUser(userId);
    }

    @Override
    public OtpRateInfo start(ForgotStartRequest req) {
        return null;
    }

    @Override
    public ResetTokenResponse verifyOtp(ForgotVerifyRequest req) {
        return null;
    }

    @Override
    public void reset(ForgotResetRequest req) {

    }
}
