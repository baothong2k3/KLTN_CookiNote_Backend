/*
 * @ (#) LoginServiceImpl.java    1.0    26/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 26/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.LoginRequest;
import fit.kltn_cookinote_backend.dtos.request.RefreshRequest;
import fit.kltn_cookinote_backend.dtos.request.TokenPair;
import fit.kltn_cookinote_backend.dtos.response.LoginResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LoginServiceImpl implements LoginService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshService;
    private final SessionAllowlistService sessionService;
    private final LoginAttemptRateLimiter loginLimiter;
    private final LoginHistoryService loginHistoryService;

    @Override
    public LoginResponse login(LoginRequest req) {

        // BƯỚC 1: Kiểm tra xem có bị chặn không (TRƯỚC KHI VÀO DB)
        loginLimiter.checkAndThrowIfBlocked(req.username());

        User user = userRepo.findByUsername(req.username())
                .or(() -> userRepo.findByEmail(req.username()))
                .orElseThrow(() -> {
                    // BƯỚC 2a: Ghi lại lỗi (nhưng không tiết lộ user có tồn tại hay không)
                    loginLimiter.recordFailedLogin(req.username());
                    // LOG WARN: Đăng nhập sai username
                    log.warn("Login Failed: User '{}' entered wrong username or password.", req.username());
                    return new IllegalArgumentException("Tài khoản hoặc mật khẩu không đúng.");
                });

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Tài khoản chưa xác thực email. Vui lòng kiểm tra email để xác thực.");
        }

        if (!user.isEnabled()) {
            throw new IllegalStateException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        if (!encoder.matches(req.password(), user.getPassword())) {
            // BƯỚC 2b: Ghi lại lỗi khi sai mật khẩu
            loginLimiter.recordFailedLogin(req.username());
            // LOG WARN: Sai mật khẩu (Rất quan trọng)
            log.warn("Login Failed: User '{}' entered wrong username or password.", req.username());
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không đúng.");
        }

        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            // BƯỚC 2c: Ghi lại lỗi (trường hợp đăng nhập Google/Facebook bằng form local)
            loginLimiter.recordFailedLogin(req.username());
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không đúng.");
        }

        // BƯỚC 3: Đăng nhập thành công -> Xóa bộ đếm
        loginLimiter.recordSuccessfulLogin(req.username());

        // LOG INFO: Đăng nhập thành công
        log.info("User Login Success: ID={}, Role={}", user.getUserId(), user.getRole());
        loginHistoryService.save(user);

        var issue = jwtService.generateAccessToken(user);
        sessionService.allow(user.getUserId(), issue.jti(), issue.expiresInSeconds());
        String refresh = refreshService.issue(user.getUserId());

        return LoginResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .displayName(user.getDisplayName())
                .tokens(TokenPair.builder()
                        .accessToken(issue.token())
                        .refreshToken(refresh)
                        .accessExpiresInSeconds(issue.expiresInSeconds())
                        .refreshExpiresInSeconds(refreshService.refreshTtlSeconds())
                        .build())
                .build();
    }

    @Override
    public TokenPair refresh(RefreshRequest req) {
        Long userId = refreshService.validate(req.refreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Refresh token không hợp lệ hoặc đã hết hạn."));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        // rotate refresh
        String newRefresh = refreshService.rotate(req.refreshToken(), userId);

        // phát hành access mới + allow jti mới
        var issue = jwtService.generateAccessToken(user);
        sessionService.allow(user.getUserId(), issue.jti(), issue.expiresInSeconds());

        return TokenPair.builder()
                .accessToken(issue.token())
                .refreshToken(newRefresh)
                .accessExpiresInSeconds(issue.expiresInSeconds())
                .refreshExpiresInSeconds(refreshService.refreshTtlSeconds())
                .build();
    }

    @Override
    public void logout(String refreshToken, @Nullable String currentAccessToken) {
        // 1) Revoke refresh
        refreshService.revoke(refreshToken);

        // 2) Revoke access hiện tại (nếu client gửi kèm)
        if (currentAccessToken != null && !currentAccessToken.isBlank()) {
            try {
                var jws = jwtService.parse(currentAccessToken);
                String jti = jws.getPayload().getId();
                if (jti != null) sessionService.revoke(jti);
            } catch (Exception ignored) {
            }
        }
    }
}
