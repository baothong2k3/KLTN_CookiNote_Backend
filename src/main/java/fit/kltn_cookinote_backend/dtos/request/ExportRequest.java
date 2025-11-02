/*
 * @ (#) ExportRequest.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import jakarta.annotation.Nullable;

public record ExportRequest(
        @Nullable // Đường dẫn có thể là null
        String customSavePath
) {
}
