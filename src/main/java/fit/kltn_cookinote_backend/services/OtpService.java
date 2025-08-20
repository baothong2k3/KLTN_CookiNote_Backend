/*
 * @ (#) OtpService.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */


import fit.kltn_cookinote_backend.entities.EmailOtp;
import fit.kltn_cookinote_backend.entities.OtpPurpose;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.EmailOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional
public class OtpService {
    private final EmailOtpRepository otpRepo;
    private final PasswordEncoder encoder;
    private final MailService mailService;
    private final OtpAttemptService attemptService;

    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    public void createAndSendEmailVerifyOtp(User user) {
        String rawOtp = generateNumericOtp(6);
        String hash = encoder.encode(rawOtp);

        EmailOtp otp = otpRepo.findByUserAndPurpose(user, OtpPurpose.EMAIL_VERIFY)
                .orElseGet(() -> EmailOtp.builder()
                        .user(user)
                        .purpose(OtpPurpose.EMAIL_VERIFY)
                        .build());

        otp.setCodeHash(hash);
        otp.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(OTP_TTL));
        otp.setAttempts(0);
        otp.setMaxAttempts(5);

        otpRepo.saveAndFlush(otp);

        mailService.sendOtp(user.getEmail(), user.getUsername(), rawOtp);
    }

    public void verifyEmailOtp(User user, String inputOtp) {
        EmailOtp otp = otpRepo.findByUserAndPurpose(user, OtpPurpose.EMAIL_VERIFY)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy OTP, hãy yêu cầu gửi lại."));

        if (otp.getAttempts() >= otp.getMaxAttempts()) {
            throw new IllegalStateException("Bạn đã vượt quá số lần nhập OTP cho phép.");
        }
        if (LocalDateTime.now(ZoneOffset.UTC).isAfter(otp.getExpiresAt())) {
            throw new IllegalStateException("OTP đã hết hạn, hãy yêu cầu gửi lại.");
        }
        if (!encoder.matches(inputOtp, otp.getCodeHash())) {
            attemptService.increaseAttempts(otp.getId());
            throw new IllegalArgumentException("OTP không chính xác.");
        }

        // Thành công: đánh dấu user đã verify + bật tài khoản
        user.setEmailVerified(true);
        user.setEnabled(true);

        // Dọn OTP
        otpRepo.delete(otp);
    }

    public void resendEmailVerifyOtp(User user) {
        //nên thêm limit gửi otp
        createAndSendEmailVerifyOtp(user);
    }

    private String generateNumericOtp(int digits) {
        SecureRandom r = new SecureRandom();
        int bound = (int) Math.pow(10, digits);
        int min = (int) Math.pow(10, digits - 1);
        int number = r.nextInt(bound - min) + min;
        return String.valueOf(number);
    }
}
