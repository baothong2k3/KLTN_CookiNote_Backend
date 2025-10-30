/*
 * @ (#) CommentResponse.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeComment;
import fit.kltn_cookinote_backend.entities.User;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Builder
public record CommentResponse(
        Long id,
        Long recipeId,
        Long parentId,
        String content,
        Long authorId,
        String authorName,
        String authorAvatar,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CommentResponse> replies
) {
    /**
     * Phương thức đệ quy để chuyển đổi Entity sang DTO,
     * bao gồm cả các bình luận trả lời.
     */
    public static CommentResponse from(RecipeComment comment) {
        if (comment == null) {
            return null;
        }

        User author = comment.getUser();

        // Chuyển đổi đệ quy các replies
        List<CommentResponse> replyDtos = comment.getReplies() != null
                ? comment.getReplies().stream()
                .map(CommentResponse::from) // Gọi đệ quy
                .collect(Collectors.toList())
                : List.of(); // Trả về danh sách rỗng nếu không có replies

        return CommentResponse.builder()
                .id(comment.getId())
                .recipeId(comment.getRecipe() != null ? comment.getRecipe().getId() : null)
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .content(comment.getContent())
                .authorId(author != null ? author.getUserId() : null)
                .authorName(author != null ? author.getDisplayName() : "Người dùng ẩn")
                .authorAvatar(author != null ? author.getAvatarUrl() : null)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .replies(replyDtos)
                .build();
    }
}
