/*
 * @ (#) CloudinaryService.java    1.0    25/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 25/08/2025
 * @version: 1.0
 */

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public interface CloudinaryService {
    String updateAvatar(Long userId, MultipartFile file) throws IOException;
    void safeDeleteByPublicId(String publicId);
    String extractPublicIdFromUrl(String url);
}
