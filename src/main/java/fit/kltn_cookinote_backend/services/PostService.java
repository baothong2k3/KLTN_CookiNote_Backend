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
}
