/*
 * @ (#) CommentService.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.CreateCommentRequest;
import fit.kltn_cookinote_backend.dtos.request.UpdateCommentRequest;
import fit.kltn_cookinote_backend.dtos.response.CommentResponse;
import fit.kltn_cookinote_backend.entities.User;

import java.util.List;

public interface CommentService {

    /**
     * Lấy tất cả bình luận (dạng cây) cho một công thức.
     */
    List<CommentResponse> getCommentsByRecipe(Long recipeId, Long viewerUserId);

    /**
     * Tạo một bình luận mới (gốc hoặc trả lời).
     */
    CommentResponse createComment(Long recipeId, User author, CreateCommentRequest request);

    /**
     * Cập nhật nội dung bình luận.
     */
    CommentResponse updateComment(Long commentId, User author, UpdateCommentRequest request);

    /**
     * Xóa một bình luận (chỉ chủ sở hữu hoặc Admin).
     */
    void deleteComment(Long commentId, User author);
}
