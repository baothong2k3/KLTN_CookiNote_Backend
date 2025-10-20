/*
 * @ (#) GoogleTokenVerifier.java    1.0    29/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.configs;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/08/2025
 * @version: 1.0
 */

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.*;
import com.nimbusds.jwt.JWTClaimsSet;
import fit.kltn_cookinote_backend.dtos.GoogleProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class GoogleTokenVerifier {
    @Value("${app.oauth.google.client-id}")
    private String clientId;

    @Value("${app.oauth.google.issuer}")
    private String issuer;

    @Value("${app.oauth.google.jwk-set-uri}")
    private String jwkSetUri;

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor() throws Exception {
        JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(jwkSetUri));
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
        processor.setJWSKeySelector(keySelector);
        return processor;
    }

    public GoogleProfile verify(String idToken) {
        try {
            var processor = buildProcessor();
            SecurityContext ctx = null;
            JWTClaimsSet claims = processor.process(SignedJWT.parse(idToken), ctx);

            // 1) issuer
            String iss = claims.getIssuer();
            if (!Objects.equals(iss, issuer) && !Objects.equals(iss, "accounts.google.com")) {
                throw new RuntimeException("Invalid issuer");
            }

            // 2) audience (chứa clientId của bạn)
            List<String> aud = claims.getAudience();
            if (aud == null || aud.stream().noneMatch(clientId::equals)) {
                throw new RuntimeException("Invalid audience");
            }

            // 3) exp
            Instant exp = claims.getExpirationTime().toInstant();
            if (Instant.now().isAfter(exp)) {
                throw new RuntimeException("ID token is expired");
            }

            // 4) lấy thông tin
            String sub = claims.getSubject();
            String email = claims.getStringClaim("email");
            Boolean emailVerified = claims.getBooleanClaim("email_verified");
            String name = claims.getStringClaim("name");
            String picture = claims.getStringClaim("picture");

            return new GoogleProfile(sub, email, Boolean.TRUE.equals(emailVerified), name, picture);
        } catch (Exception e) {
            throw new RuntimeException("Invalid Google ID token: " + e.getMessage(), e);
        }
    }
}
