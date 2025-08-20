/*
 * @ (#) OtpAttemptService.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.repositories.EmailOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OtpAttemptService {
    private final EmailOtpRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseAttempts(Long otpId) {
        repo.increaseAttempts(otpId);
    }
}
