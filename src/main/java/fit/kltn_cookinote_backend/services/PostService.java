/*
 * @ (#) PostService.java    1.0    02/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 02/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.dtos.response.PostResponse;
import fit.kltn_cookinote_backend.entities.User;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PostService {

    /**
     * Admin tạo bài viết mới (bắt buộc có ảnh)
     */
    PostResponse createPost(User adminUser, String title, String content, MultipartFile image) throws IOException;

    /**
     * Admin cập nhật nội dung bài viết (không đổi ảnh)
     */
    PostResponse updatePostContent(Long postId, User adminUser, String title, String content);

    /**
     * Admin cập nhật ảnh bài viết
     */
    PostResponse updatePostImage(Long postId, User adminUser, MultipartFile image) throws IOException;
}
