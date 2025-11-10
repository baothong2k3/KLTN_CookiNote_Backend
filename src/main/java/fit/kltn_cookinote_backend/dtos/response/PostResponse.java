/*
 * @ (#) PostResponse.java    1.0    02/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.dtos.response;/*
 * @description:
 * @author: Bao Thong
 * @date: 02/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.Post;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Role;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostResponse(
        Long id,
        String title,
        String content,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long authorId,
        String authorName,
        String authorAvatarUrl,
        Role role
) {
    public static PostResponse from(Post post) {
        User author = post.getAuthor();
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .authorId(author != null ? author.getUserId() : null)
                .authorName(author != null ? author.getDisplayName() : "Admin")
                .authorAvatarUrl(author != null ? author.getAvatarUrl() : null)
                .role(author != null ? author.getRole() : Role.ADMIN)
                .build();
    }
}
