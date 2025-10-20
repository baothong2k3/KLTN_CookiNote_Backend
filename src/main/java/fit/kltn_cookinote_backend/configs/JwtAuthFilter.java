/*
 * @ (#) JwtAuthFilter.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.configs;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.JwtService;
import fit.kltn_cookinote_backend.services.SessionAllowlistService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final SessionAllowlistService sessionService;
    private final AuthenticationEntryPoint authEntryPoint;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                var jws = jwtService.parse(token);

                String jti = jws.getPayload().getId();
                if (jti == null || !sessionService.isAllowed(jti)) {
                    authEntryPoint.commence(request, response,
                            new InsufficientAuthenticationException("revoked or not allowed"));
                    return;
                }

                Long userId = Long.valueOf(jws.getPayload().getSubject());

                // Lấy iat (Date) rồi chuyển sang Instant để so sánh với passwordChangedAt (LocalDateTime)
                Date iatDate = jws.getPayload().getIssuedAt();
                Instant iat = (iatDate != null) ? iatDate.toInstant() : null;

                // Tải user & dựng Authentication
                User user = userRepo.findById(userId).orElse(null);

                // Chủ động từ chối nếu user không hợp lệ (thay vì lờ đi và cho request đi tiếp)
                if (user == null || !user.isEnabled() || !user.isEmailVerified()) {
                    authEntryPoint.commence(request, response,
                            new InsufficientAuthenticationException("user disabled or unverified"));
                    return;
                }

                // Vô hiệu hoá access token nếu phát hành trước thời điểm đổi mật khẩu
                if (user.getPasswordChangedAt() != null) {
                    Instant pwdChangedAtInstant = user.getPasswordChangedAt()
                            .atOffset(ZoneOffset.UTC)
                            .toInstant();

                    if (iat == null || iat.isBefore(pwdChangedAtInstant)) {
                        authEntryPoint.commence(request, response,
                                new InsufficientAuthenticationException("token invalidated by password change"));
                        return;
                    }
                }

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                authEntryPoint.commence(request, response, new InsufficientAuthenticationException("token expired", e));
                return;
            } catch (Exception e) {
                authEntryPoint.commence(request, response, new BadCredentialsException("invalid token", e));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
