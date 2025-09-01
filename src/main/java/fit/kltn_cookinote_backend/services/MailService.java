/*
 * @ (#) MailService.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;

    public void sendOtp(String to, String username, String otp) {
        var message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[CookiNote] Xác thực email");
        message.setText("""
                Xin chào, %s!
                
                Mã OTP xác thực email của bạn là: %s
                Mã có hiệu lực trong 5 phút.
                
                Cảm ơn bạn!
                """.formatted(username, otp));
        mailSender.send(message);
    }

    public void sendWelcome(String to, String username) {
        var message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[CookiNote] Chào mừng bạn đến với CookiNote");
        message.setText("""
                Xin chào, %s!
                
                Chào mừng bạn đến với CookiNote! Chúng tôi rất vui khi có bạn tham gia cùng chúng tôi.
                
                Hãy bắt đầu hành trình ẩm thực của bạn với CookiNote ngay hôm nay!
                """.formatted(username));
        mailSender.send(message);
    }
}
