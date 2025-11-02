/*
 * @ (#) CreateCommentRequest.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.request;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "Nội dung bình luận không được để trống")
        @Size(max = 2048, message = "Nội dung tối đa 2048 ký tự")
        String content,

        @Nullable // Có thể null nếu là bình luận gốc
        Long parentId
) {
}
