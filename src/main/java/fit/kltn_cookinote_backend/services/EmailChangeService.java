/*
 * @ (#) EmailChangeService.java    1.0    24/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 24/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.ChangeEmailRequest;
import fit.kltn_cookinote_backend.dtos.request.VerifyEmailChangeRequest;
import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.entities.User;
import org.springframework.stereotype.Service;

@Service
public interface EmailChangeService {

    OtpRateInfo requestChange(User user, ChangeEmailRequest req);

    OtpRateInfo resendOtp(User user, ChangeEmailRequest req);

    void verifyAndCommit(User user, VerifyEmailChangeRequest req);
}
