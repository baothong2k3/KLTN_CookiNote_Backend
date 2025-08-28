/*
 * @ (#) GoogleAuthServiceImpl.java    1.0    29/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.configs.GoogleTokenVerifier;
import fit.kltn_cookinote_backend.dtos.request.TokenPair;
import fit.kltn_cookinote_backend.dtos.response.LoginResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.AuthProvider;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor

public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final GoogleTokenVerifier verifier;
    private final UserRepository userRepo;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SessionAllowlistService sessionAllowlistService;
    private final MailService mailService;

    @Override
    public LoginResponse loginWithGoogle(String idToken) {
        var p = verifier.verify(idToken);
        if (p.email() == null || p.email().isBlank())
            throw new IllegalStateException("Không lấy được email từ Google. Hãy cấp quyền email.");

        String name = (p.name() != null && !p.name().isBlank()) ? p.name() : null;
        String email = p.email();
        String picture = p.picture();

        var user = userRepo.findByEmail(email).map(u -> {
            if (u.getAuthProvider() != AuthProvider.GOOGLE)
                throw new IllegalStateException("Email này đã có tài khoản. Hãy đăng nhập bằng mật khẩu.");
            if (p.emailVerified() && !u.isEmailVerified()) u.setEmailVerified(true);
            if ((u.getAvatarUrl() == null || u.getAvatarUrl().isBlank()) && picture != null)
                u.setAvatarUrl(picture);
            if ((u.getDisplayName() == null || u.getDisplayName().isBlank()) && name != null)
                u.setDisplayName(name);
            if (!u.isEnabled()) u.setEnabled(true);
            return userRepo.save(u);
        }).orElseGet(() -> {
            var u = new User();
            u.setUsername(name != null ? name
                    : email.contains("@") ? email.split("@")[0]
                    : "gg_" + UUID.randomUUID().toString().substring(0, 8));
            u.setDisplayName(name != null ? name : u.getUsername());
            u.setEmail(email);
            u.setAvatarUrl(picture);
            u.setRole(Role.USER);
            u.setAuthProvider(AuthProvider.GOOGLE);
            u.setEmailVerified(p.emailVerified());
            u.setEnabled(true);
            u.setPassword(null);
            mailService.sendWelcome(email, u.getDisplayName());
            return userRepo.save(u);
        });

        // Access + allow-list
        var access = jwtService.generateAccessToken(user);
        sessionAllowlistService.allow(user.getUserId(), access.jti(), access.expiresInSeconds());

        // Refresh
        String refresh = refreshTokenService.issue(user.getUserId());

        var tokens = TokenPair.builder()
                .accessToken(access.token())
                .refreshToken(refresh)
                .accessExpiresInSeconds(access.expiresInSeconds())
                .refreshExpiresInSeconds(refreshTokenService.refreshTtlSeconds())
                .build();

        return LoginResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .tokens(tokens)
                .build();
    }
}
