/*
 * @ (#) CommentController.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.CreateCommentRequest;
import fit.kltn_cookinote_backend.dtos.request.UpdateCommentRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.CommentResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.services.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * API để lấy tất cả bình luận (dạng cây) cho một công thức.
     * Ai cũng có thể xem nếu công thức là PUBLIC/SHARED.
     * Endpoint: GET /recipes/{recipeId}
     */
    @GetMapping("/recipes/{recipeId}")
    @PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập để biết viewerId
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal User authUser,
            HttpServletRequest httpReq
    ) {
        List<CommentResponse> data = commentService.getCommentsByRecipe(recipeId, authUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bình luận thành công", data, httpReq.getRequestURI()));
    }

    /**
     * API để tạo bình luận mới (gốc hoặc trả lời).
     * Endpoint: POST /recipes/{recipeId}
     * Body: { "content": "...", "parentId": null (hoặc ID) }
     */
    @PostMapping("/recipes/{recipeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest httpReq
    ) {
        CommentResponse data = commentService.createComment(recipeId, authUser, request);
        return ResponseEntity.ok(ApiResponse.success("Đăng bình luận thành công", data, httpReq.getRequestURI()));
    }

    /**
     * API để cập nhật bình luận (chỉ chủ sở hữu).
     * Endpoint: PUT /comments/{commentId}
     */
    @PutMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal User authUser,
            @Valid @RequestBody UpdateCommentRequest request,
            HttpServletRequest httpReq
    ) {
        CommentResponse data = commentService.updateComment(commentId, authUser, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bình luận thành công", data, httpReq.getRequestURI()));
    }

    /**
     * API để xóa bình luận (chủ sở hữu, chủ recipe, hoặc Admin).
     * Endpoint: DELETE /comments/{commentId}
     */
    @DeleteMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal User authUser,
            HttpServletRequest httpReq
    ) {
        commentService.deleteComment(commentId, authUser);
        return ResponseEntity.ok(ApiResponse.success("Xóa bình luận thành công", httpReq.getRequestURI()));
    }
}
