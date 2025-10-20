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


import fit.kltn_cookinote_backend.dtos.response.OtpRateInfo;
import fit.kltn_cookinote_backend.dtos.request.RateWindow;
import fit.kltn_cookinote_backend.entities.EmailOtp;
import fit.kltn_cookinote_backend.enums.OtpPurpose;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.limiters.RedisOtpRateLimiter;
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
    private final RedisOtpRateLimiter rateLimiter;

    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    public OtpRateInfo createAndSendOtp(User user, String email, OtpPurpose purpose) {

        rateLimiter.consumeOrThrow(user.getUserId(), purpose.name());

        String rawOtp = generateNumericOtp(6);
        String hash = encoder.encode(rawOtp);

        EmailOtp otp = otpRepo.findByUserAndPurpose(user, purpose)
                .orElseGet(() -> EmailOtp.builder()
                        .user(user)
                        .purpose(purpose)
                        .build());

        otp.setCodeHash(hash);
        otp.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plus(OTP_TTL));
        otp.setAttempts(0);
        otp.setMaxAttempts(5);

        otpRepo.saveAndFlush(otp);

        mailService.sendOtp(email, user.getUsername(), rawOtp);

        RateWindow w = rateLimiter.currentWindow(user.getUserId(), purpose.name());
        long remaining = Math.max(0, w.limit() - w.used());
        return OtpRateInfo.builder()
                .used(w.used())
                .limit(w.limit())
                .remaining(remaining)
                .resetAfter(w.ttlSeconds())
                .build();
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

    public void createAndSendEmailVerifyOtp(User user) {
        createAndSendOtp(user, user.getEmail(), OtpPurpose.EMAIL_VERIFY);
    }

    public OtpRateInfo createAndSendEmailChangeOtp(User user, String newEmail) {
        return createAndSendOtp(user, newEmail, OtpPurpose.EMAIL_CHANGE);
    }

    public OtpRateInfo createAndSendEmailResetPassword(User user) {
        return createAndSendOtp(user, user.getEmail(), OtpPurpose.PASSWORD_RESET);
    }

    public OtpRateInfo resendEmailVerifyOtp(User user) {
        return createAndSendOtp(user, user.getEmail(), OtpPurpose.EMAIL_VERIFY);
    }

    private String generateNumericOtp(int digits) {
        SecureRandom r = new SecureRandom();
        int bound = (int) Math.pow(10, digits);
        int min = (int) Math.pow(10, digits - 1);
        int number = r.nextInt(bound - min) + min;
        return String.valueOf(number);
    }

    public void verifyEmailChangeOtp(User user, String inputOtp) {
        EmailOtp otp = otpRepo.findByUserAndPurpose(user, OtpPurpose.EMAIL_CHANGE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy OTP, hãy yêu cầu gửi lại."));

        if (otp.getAttempts() >= otp.getMaxAttempts()) {
            throw new IllegalStateException("Bạn đã vượt quá số lần nhập OTP cho phép.");
        }
        if (LocalDateTime.now(ZoneOffset.UTC).isAfter(otp.getExpiresAt())) {
            throw new IllegalStateException("OTP đã hết hạn, hãy yêu cầu gửi lại.");
        }
        if (!encoder.matches(inputOtp, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1); // dirty checking
            throw new IllegalArgumentException("OTP không chính xác.");
        }

        // Thành công -> xóa OTP (đổi email sẽ được làm tại EmailChangeService)
        otpRepo.delete(otp);
    }

    private EmailOtp findAndValidateOtp(User user, OtpPurpose purpose, String rawOtp) {
        EmailOtp otp = otpRepo.findByUserAndPurpose(user, purpose)
                .orElseThrow(() -> new IllegalStateException("OTP không hợp lệ hoặc đã hết hạn"));

        // hết hạn?
        if (otp.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            otpRepo.delete(otp);
            throw new IllegalStateException("OTP không hợp lệ hoặc đã hết hạn");
        }

        // quá số lần?
        if (otp.getAttempts() >= otp.getMaxAttempts()) {
            otpRepo.delete(otp);
            throw new IllegalStateException("Bạn đã nhập sai quá số lần cho phép");
        }

        // sai → tăng attempts
        if (!encoder.matches(rawOtp, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepo.save(otp);
            throw new IllegalStateException("OTP không hợp lệ hoặc đã hết hạn");
        }
        return otp;
    }

    public void verifyAndConsumeOtpOrThrow(User user, OtpPurpose purpose, String rawOtp) {
        EmailOtp otp = findAndValidateOtp(user, purpose, rawOtp);
        // đúng → consume one-time
        otpRepo.delete(otp);
    }

    public void checkOtp(User user, OtpPurpose purpose, String rawOtp) {
        findAndValidateOtp(user, purpose, rawOtp);
    }
}
