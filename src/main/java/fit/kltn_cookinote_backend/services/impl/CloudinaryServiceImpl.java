/*
 * @ (#) CloudinaryServiceImpl.java    1.0    26/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 26/08/2025
 * @version: 1.0
 */

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.CloudinaryService;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {
    private final Cloudinary cloudinary;
    private final UserRepository userRepo;

    @Value("${app.cloudinary.folder}")
    private String avatarFolder;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    private static final Pattern VERSION_SEGMENT = Pattern.compile("v\\d+/");

    @Override
    @Transactional
    public String updateAvatar(Long userId, MultipartFile file) throws IOException {
        ImageValidationUtils.validateImage(file);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại."));

        // (1) Lưu URL cũ
        final String oldUrl = user.getAvatarUrl();

        // (2) Upload ảnh mới với public_id mới → an toàn để xóa ảnh cũ theo URL
        String newPublicId = "u_" + userId + "_" + Instant.now().getEpochSecond(); // hoặc kèm random nếu muốn
        Map<?, ?> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", avatarFolder,
                        "public_id", newPublicId,
                        "overwrite", false,           // mỗi lần là một asset mới
                        "unique_filename", false,
                        "resource_type", "image"
                )
        );

        String newUrl = (String) uploadResult.get("secure_url");
        if (!StringUtils.hasText(newUrl)) throw new IllegalStateException("Upload Cloudinary thất bại.");

        // (3) Cập nhật DB với URL mới
        user.setAvatarUrl(newUrl);
        userRepo.save(user);

        // (4) Sau khi COMMIT thành công, xóa ảnh cũ theo URL đã lưu
        if (StringUtils.hasText(oldUrl)) {
            final String oldPublicId = extractPublicIdFromUrl(oldUrl);
            if (StringUtils.hasText(oldPublicId)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        safeDeleteByPublicId(oldPublicId);
                    }
                });
            }
        }

        return newUrl;
    }

    private void safeDeleteByPublicId(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "invalidate", true,
                    "resource_type", "image"
            ));
        } catch (Exception ignore) {
            // log.warn("Không thể xóa ảnh cũ trên Cloudinary: {}", publicId, ignore);
        }
    }

    /**
     * Trích xuất public_id (kèm folder) từ URL Cloudinary.
     * Hỗ trợ URL có transform & version, ví dụ:
     * .../image/upload/c_scale,w_200/v1724550000/cookinote/avatars/u_1_1724550123.png
     * -> trả về: cookinote/avatars/u_1_1724550123
     */
    private String extractPublicIdFromUrl(String url) {
        try {
            URL u = new URL(url);
            String path = u.getPath(); // /<cloud>/image/upload/[transform/...]v1234567890/<folder>/<name>.<ext>
            int uploadIdx = path.indexOf("/upload/");
            if (uploadIdx < 0) return null;

            String rest = path.substring(uploadIdx + "/upload/".length());
            Matcher m = VERSION_SEGMENT.matcher(rest);
            if (m.find()) {
                rest = rest.substring(m.end());
            }

            int q = rest.indexOf('?');
            if (q >= 0) rest = rest.substring(0, q);
            int h = rest.indexOf('#');
            if (h >= 0) rest = rest.substring(0, h);
            int dot = rest.lastIndexOf('.');
            if (dot > 0) rest = rest.substring(0, dot);

            return rest.isBlank() ? null : rest;
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
