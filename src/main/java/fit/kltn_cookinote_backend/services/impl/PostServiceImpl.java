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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    @Override
    @Transactional
    public PostResponse updatePostContent(Long postId, User adminUser, String title, String content) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bài viết: " + postId));

        // Mặc dù đã check @PreAuthorize, service nên check lại author
        if (!post.getAuthor().getUserId().equals(adminUser.getUserId())) {
            throw new SecurityException("Bạn không có quyền chỉnh sửa bài viết này");
        }

        if (StringUtils.hasText(title)) {
            post.setTitle(title);
        }
        if (StringUtils.hasText(content)) {
            post.setContent(content);
        }

        post.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        Post updatedPost = postRepository.save(post);
        return PostResponse.from(updatedPost);
    }

    @Override
    @Transactional
    public PostResponse updatePostImage(Long postId, User adminUser, MultipartFile image) throws IOException {
        ImageValidationUtils.validateImage(image);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bài viết: " + postId));

        final String oldUrl = post.getImageUrl();

        // Upload ảnh mới
        String publicId = "p_" + post.getId() + "_" + Instant.now().getEpochSecond();
        String newUrl = CloudinaryUtils.uploadImage(cloudinary, image, postFolder, publicId);

        // Cập nhật DB
        post.setImageUrl(newUrl);
        Post updatedPost = postRepository.save(post);

        // Xóa ảnh cũ (nếu có) sau khi commit DB thành công
        if (StringUtils.hasText(oldUrl)) {
            String oldPublicId = cloudinaryService.extractPublicIdFromUrl(oldUrl);
            if (StringUtils.hasText(oldPublicId)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cloudinaryService.safeDeleteByPublicId(oldPublicId);
                    }
                });
            }
        }

        return PostResponse.from(updatedPost);
    }

    @Override
    @Transactional
    public void deletePost(Long postId, User adminUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bài viết: " + postId));

        final String imageUrl = post.getImageUrl();

        // Xóa khỏi DB
        postRepository.delete(post);

        // Xóa ảnh trên Cloudinary sau khi commit
        if (StringUtils.hasText(imageUrl)) {
            String publicId = cloudinaryService.extractPublicIdFromUrl(imageUrl);
            if (StringUtils.hasText(publicId)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cloudinaryService.safeDeleteByPublicId(publicId);
                    }
                });
            }
        }
    }
}
