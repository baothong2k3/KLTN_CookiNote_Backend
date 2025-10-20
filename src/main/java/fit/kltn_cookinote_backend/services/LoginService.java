/*
 * @ (#) LoginService.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.LoginRequest;
import fit.kltn_cookinote_backend.dtos.request.RefreshRequest;
import fit.kltn_cookinote_backend.dtos.request.TokenPair;
import fit.kltn_cookinote_backend.dtos.response.LoginResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public interface LoginService {

    LoginResponse login(LoginRequest req);

    TokenPair refresh(RefreshRequest req);

    void logout(String refreshToken, @Nullable String currentAccessToken);
}
