/*
 * @ (#) AuthService.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import org.springframework.stereotype.Service;

@Service
public interface AuthService {
    void register(RegisterRequest req);

    void verifyEmail(VerifyOtpRequest req);

    OtpRateInfo resendOtp(ResendOtpRequest req);

    OtpRateInfo startForgotPassword(ForgotStartRequest req);

    void resetPasswordWithOtp(ForgotVerifyRequest req);
}
