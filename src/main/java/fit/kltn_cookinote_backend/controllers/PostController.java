/*
 * @ (#) PostController.java    1.0    02/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 02/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.dtos.response.PostResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * API Admin tạo bài viết mới (gồm title, content, image)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @AuthenticationPrincipal User adminUser,
            @RequestParam(value = "title") @NotBlank String title,
            @RequestParam(value = "content") @NotBlank String content,
            @RequestPart(value = "image") MultipartFile image,
            HttpServletRequest httpReq
    ) throws IOException {
        PostResponse data = postService.createPost(adminUser, title, content, image);
        return ResponseEntity.ok(ApiResponse.success("Tạo bài viết thành công", data, httpReq.getRequestURI()));
    }

    /**
     * API Admin cập nhật nội dung bài viết (title, content)
     */
    @PutMapping(value = "/{postId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostContent(
            @AuthenticationPrincipal User adminUser,
            @PathVariable Long postId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            HttpServletRequest httpReq
    ) {
        PostResponse data = postService.updatePostContent(postId, adminUser, title, content);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nội dung bài viết thành công", data, httpReq.getRequestURI()));
    }

    /**
     * API Admin cập nhật ảnh bài viết
     */
    @PutMapping(value = "/{postId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostImage(
            @AuthenticationPrincipal User adminUser,
            @PathVariable Long postId,
            @RequestPart("image") MultipartFile image,
            HttpServletRequest httpReq
    ) throws IOException {
        PostResponse data = postService.updatePostImage(postId, adminUser, image);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh bài viết thành công", data, httpReq.getRequestURI()));
    }

    /**
     * API Admin xóa bài viết
     */
    @DeleteMapping("/{postId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal User adminUser,
            @PathVariable Long postId,
            HttpServletRequest httpReq
    ) {
        postService.deletePost(postId, adminUser);
        return ResponseEntity.ok(ApiResponse.success("Xóa bài viết thành công", httpReq.getRequestURI()));
    }

    /**
     * API Lấy danh sách bài viết (công khai, phân trang)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<PostResponse>>> getAllPosts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            HttpServletRequest httpReq
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResult<PostResponse> data = postService.getAllPosts(pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bài viết thành công", data, httpReq.getRequestURI()));
    }
}
