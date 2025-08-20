/*
 * @ (#) OtpRateInfo.java    1.0    20/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos;/*
 * @description:
 * @author: Bao Thong
 * @date: 20/08/2025
 * @version: 1.0
 */

import lombok.Builder;

@Builder
public record OtpRateInfo(
        long used,       // đã dùng trong giờ hiện tại
        long limit,      // giới hạn
        long remaining,  // còn lại
        long resetAfter  // còn bao giây thì reset cửa sổ
) {
}
