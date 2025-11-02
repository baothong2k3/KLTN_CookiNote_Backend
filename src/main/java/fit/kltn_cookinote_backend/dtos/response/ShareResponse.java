/*
 * @ (#) ShareResponse.java    1.0    28/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/10/2025
 * @version: 1.0
 */

import lombok.Builder;

@Builder
public record ShareResponse(
        String shareCode,   // Mã chia sẻ ngắn gọn
        String shareUrl,    // URL đầy đủ để truy cập
        String qrCodeBase64 // Ảnh QR dạng Base64
) {
}
