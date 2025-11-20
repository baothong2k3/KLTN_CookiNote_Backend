/*
 * @ (#) UserLoginHistoryResponse.java    1.0    20/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.UserLoginHistory;
import lombok.Builder;
import ua_parser.Client;
import ua_parser.Parser;

import java.time.LocalDateTime;

@Builder
public record UserLoginHistoryResponse(
        Long id,
        LocalDateTime loginTime,
        String ipAddress,
        String userAgent,
        String browser,
        String os
) {
    // Khởi tạo Parser một lần duy nhất để tối ưu hiệu năng (Static)
    private static final Parser uaParser;

    static {
        try {
            uaParser = new Parser();
        } catch (Exception e) {
            throw new RuntimeException("Không thể khởi tạo User Agent Parser", e);
        }
    }

    public static UserLoginHistoryResponse from(UserLoginHistory history) {
        String rawUserAgent = history.getUserAgent();
        String browserName = "Unknown";
        String osName = "Unknown";

        // Phân tích User Agent
        if (rawUserAgent != null) {
            Client c = uaParser.parse(rawUserAgent);

            // Lấy tên trình duyệt (Ví dụ: Chrome, Mobile Safari, PostmanRuntime)
            browserName = c.userAgent.family;
            if (c.userAgent.major != null) {
                browserName += " " + c.userAgent.major;
            }

            // Lấy tên hệ điều hành (Ví dụ: Android, iOS, Windows 10)
            osName = c.os.family;
            if (c.os.major != null) {
                osName += " " + c.os.major;
            }
        }

        return UserLoginHistoryResponse.builder()
                .id(history.getId())
                .loginTime(history.getLoginTime())
                .ipAddress(history.getIpAddress())
                .userAgent(rawUserAgent)
                .browser(browserName) // Đã được phân tích
                .os(osName)           // Đã được phân tích
                .build();
    }
}
