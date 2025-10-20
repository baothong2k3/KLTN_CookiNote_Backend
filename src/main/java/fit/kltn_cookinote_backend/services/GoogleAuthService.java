/*
 * @ (#) GoogleAuthService.java    1.0    29/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 29/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.LoginResponse;
import org.springframework.stereotype.Service;

@Service
public interface GoogleAuthService {
    LoginResponse loginWithGoogle(String idToken);
}