/*
 * @ (#) CloudinaryUtils.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.utils;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class CloudinaryUtils {

    public static String uploadImage(Cloudinary cloudinary, MultipartFile file, String folder, String publicId) throws IOException {
        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", folder,
                        "public_id", publicId,
                        "overwrite", false,
                        "unique_filename", false,
                        "resource_type", "image"
                )
        );

        String url = (String) result.get("secure_url");
        if (!StringUtils.hasText(url)) {
            log.error("Cloudinary Upload Failed: {}", url);
            throw new IllegalStateException("Upload Cloudinary thất bại.");
        }
        return url;
    }
}
