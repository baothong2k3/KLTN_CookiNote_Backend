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
import fit.kltn_cookinote_backend.services.JwtService;
import fit.kltn_cookinote_backend.services.LoginService;
import fit.kltn_cookinote_backend.services.RefreshTokenService;
import fit.kltn_cookinote_backend.services.SessionAllowlistService;
import org.springframework.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoginServiceImpl implements LoginService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshService;
    private final SessionAllowlistService sessionService;

    @Override
    public LoginResponse login(LoginRequest req) {
        User user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản hoặc mật khẩu không đúng."));
        if (!user.isEnabled() || !user.isEmailVerified()) {
            throw new IllegalStateException("Tài khoản chưa kích hoạt hoặc chưa xác thực email.");
        }
        if (!encoder.matches(req.password(), user.getPassword())) {
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không đúng.");
        }
        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không đúng.");
        }

        var issue = jwtService.generateAccessToken(user);
        sessionService.allow(user.getUserId(), issue.jti(), issue.expiresInSeconds());
        String refresh = refreshService.issue(user.getUserId());

        return LoginResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
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
