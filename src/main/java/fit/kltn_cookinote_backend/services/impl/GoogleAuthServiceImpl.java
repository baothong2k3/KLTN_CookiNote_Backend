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

import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final GoogleTokenVerifier verifier;
    private final UserRepository userRepo;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SessionAllowlistService sessionAllowlistService;
    private final MailService mailService;

    // Pattern để loại bỏ dấu tiếng Việt
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    // Pattern để giữ lại chỉ chữ cái và số
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * Helper function để chuyển chuỗi tiếng Việt có dấu thành không dấu và chỉ giữ lại chữ cái/số.
     */
    private String normalizeUsername(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        // 1. Chuẩn hóa Unicode (NFD - Canonical Decomposition)
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        // 2. Loại bỏ dấu
        String withoutDiacritics = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        // Xử lý chữ 'đ'/'Đ' riêng
        withoutDiacritics = withoutDiacritics.replaceAll("[đĐ]", "d");
        // 3. Loại bỏ ký tự không phải chữ/số
        String alphanumeric = NON_ALPHANUMERIC_PATTERN.matcher(withoutDiacritics).replaceAll("");
        // 4. Chuyển về chữ thường
        return alphanumeric.toLowerCase();
    }


    @Override
    public LoginResponse loginWithGoogle(String idToken) {
        var p = verifier.verify(idToken);
        if (p.email() == null || p.email().isBlank())
            throw new IllegalStateException("Không lấy được email từ Google. Hãy cấp quyền email.");

        String googleName = (p.name() != null && !p.name().isBlank()) ? p.name() : null;
        String email = p.email();
        String picture = p.picture();

        var user = userRepo.findByEmail(email).map(u -> {
            if (u.getAuthProvider() != AuthProvider.GOOGLE)
                throw new IllegalStateException("Email này đã có tài khoản. Hãy đăng nhập bằng mật khẩu.");
            if (p.emailVerified() && !u.isEmailVerified()) u.setEmailVerified(true);
            if ((u.getAvatarUrl() == null || u.getAvatarUrl().isBlank()) && picture != null)
                u.setAvatarUrl(picture);
            if (!u.isEnabled()) u.setEnabled(true);
            return userRepo.save(u);
        }).orElseGet(() -> {
            var u = new User();

            String baseUsername;
            if (googleName != null) {
                baseUsername = normalizeUsername(googleName); // Chuyển tên Google thành không dấu, không ký tự đặc biệt
            } else if (email.contains("@")) {
                baseUsername = email.split("@")[0]; // Lấy phần trước @ từ email nếu không có tên
                baseUsername = normalizeUsername(baseUsername); // Cũng chuẩn hóa phần email
            } else {
                baseUsername = "user"; // Fallback nếu cả tên và email đều không hợp lệ
            }
            // Thêm hậu tố UUID để đảm bảo duy nhất
            String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
            String finalUsername = baseUsername + uniqueSuffix;
            // Cắt bớt nếu quá dài (giới hạn 100 ký tự)
            if (finalUsername.length() > 100) {
                finalUsername = finalUsername.substring(0, 100);
            }
            u.setUsername(finalUsername);


            // Giữ lại tên gốc có dấu cho DisplayName
            u.setDisplayName(googleName != null ? googleName : u.getUsername());
            u.setEmail(email);
            u.setAvatarUrl(picture);
            u.setRole(Role.USER);
            u.setAuthProvider(AuthProvider.GOOGLE);
            u.setEmailVerified(p.emailVerified());
            u.setEnabled(true);
            u.setPassword(null); // Tài khoản Google không có mật khẩu local
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
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .tokens(tokens)
                .build();
    }
}