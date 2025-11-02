/*
 * @ (#) PostServiceImpl.java    1.0    02/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 02/11/2025
 * @version: 1.0
 */

import com.cloudinary.Cloudinary;
import fit.kltn_cookinote_backend.dtos.response.PostResponse;
import fit.kltn_cookinote_backend.entities.Post;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.PostRepository;
import fit.kltn_cookinote_backend.services.CloudinaryService;
import fit.kltn_cookinote_backend.services.PostService;
import fit.kltn_cookinote_backend.utils.CloudinaryUtils;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    private final PostRepository postRepository;
    private final CloudinaryService cloudinaryService;
    private final Cloudinary cloudinary;

    @Value("${app.cloudinary.post-folder}")
    private String postFolder;

    @Override
    @Transactional
    public PostResponse createPost(User adminUser, String title, String content, MultipartFile image) throws IOException {
        ImageValidationUtils.validateImage(image);

        // 1. Tạo Post entity trước để lấy ID
        Post post = Post.builder()
                .author(adminUser)
                .title(title)
                .content(content)
                .build();
        Post savedPost = postRepository.saveAndFlush(post); // Lưu để lấy ID

        // 2. Upload ảnh với ID của post
        String publicId = "p_" + savedPost.getId() + "_" + Instant.now().getEpochSecond();
        String imageUrl = CloudinaryUtils.uploadImage(cloudinary, image, postFolder, publicId);

        // 3. Cập nhật lại URL ảnh cho post
        savedPost.setImageUrl(imageUrl);
        postRepository.save(savedPost);

        return PostResponse.from(savedPost);
    }
}
