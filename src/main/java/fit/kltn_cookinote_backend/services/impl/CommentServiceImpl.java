/*
 * @ (#) CommentServiceImpl.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.CreateCommentRequest;
import fit.kltn_cookinote_backend.dtos.request.UpdateCommentRequest;
import fit.kltn_cookinote_backend.dtos.response.CommentResponse;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeComment;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.RecipeCommentRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.services.CommentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final RecipeRepository recipeRepository;
    private final RecipeCommentRepository commentRepository;

    // Helper kiểm tra quyền xem công thức
    private boolean canView(Privacy privacy, Long ownerId, Long viewerId) {
        return switch (privacy) {
            case PUBLIC, SHARED -> true;
            case PRIVATE -> viewerId != null && viewerId.equals(ownerId);
        };
    }

    // Helper tải và kiểm tra quyền xem Recipe
    private Recipe loadAndCheckRecipeViewable(Long recipeId, Long viewerId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy công thức: " + recipeId));

        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Công thức đã bị xóa: " + recipeId);
        }

        Long ownerId = recipe.getUser() != null ? recipe.getUser().getUserId() : null;
        if (!canView(recipe.getPrivacy(), ownerId, viewerId)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này.");
        }
        return recipe;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByRecipe(Long recipeId, Long viewerId) {
        // 1. Kiểm tra quyền xem công thức
        loadAndCheckRecipeViewable(recipeId, viewerId);

        // 2. Tải các bình luận cấp cao nhất (đã fetch user)
        List<RecipeComment> topLevelComments = commentRepository.findTopLevelCommentsByRecipeId(recipeId);

        // 3. Tải đệ quy các replies (sử dụng Hibernate Eager/Lazy loading)
        // Chúng ta cần đảm bảo các replies được tải
        topLevelComments.forEach(this::initializeRepliesRecursively);

        // 4. Chuyển đổi sang DTO
        return topLevelComments.stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Helper đệ quy để buộc Hibernate tải tất cả các cấp trả lời
     */
    private void initializeRepliesRecursively(RecipeComment comment) {
        Hibernate.initialize(comment.getReplies()); // Tải collection replies
        Hibernate.initialize(comment.getUser());    // Đảm bảo user của comment hiện tại được tải
        for (RecipeComment reply : comment.getReplies()) {
            initializeRepliesRecursively(reply); // Đệ quy cho từng reply
        }
    }


    @Override
    @Transactional
    public CommentResponse createComment(Long recipeId, User author, CreateCommentRequest request) {
        // 1. Kiểm tra quyền xem/bình luận trên công thức
        Recipe recipe = loadAndCheckRecipeViewable(recipeId, author.getUserId());

        RecipeComment parent = null;
        // 2. Nếu là một reply (có parentId), tải và kiểm tra comment cha
        if (request.parentId() != null) {
            // *** SỬA ĐỔI: Tải parent VÀ user của nó (sử dụng query đã tạo) ***
            parent = commentRepository.findByIdWithUser(request.parentId())
                    .orElseThrow(() -> new EntityNotFoundException("Bình luận cha không tồn tại: " + request.parentId()));

            // Đảm bảo bình luận cha thuộc cùng một công thức
            if (!Objects.equals(parent.getRecipe().getId(), recipeId)) {
                throw new IllegalArgumentException("Bình luận cha không thuộc về công thức này.");
            }
        }

        // 3. Tạo bình luận mới
        RecipeComment newComment = RecipeComment.builder()
                .recipe(recipe)
                .user(author)
                .content(request.content())
                .parent(parent) // Set cha (nếu có)
                .build();

        // *** SỬA ĐỔI: Nếu là reply, thêm vào danh sách replies của cha ***
        if (parent != null) {
            // Khởi tạo collection replies nếu nó đang là lazy/null
            Hibernate.initialize(parent.getReplies());
            // Thêm comment mới vào collection của cha
            parent.getReplies().add(newComment);
        }

        // 4. Lưu bình luận mới
        RecipeComment savedComment = commentRepository.save(newComment);

        // 5. Trả về DTO
        // Không cần tải lại vì 'savedComment' đã được quản lý (managed)
        // và 'author' (User) cũng đã được quản lý.
        return CommentResponse.from(savedComment);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, User author, UpdateCommentRequest request) {
        RecipeComment comment = commentRepository.findByIdWithUser(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Bình luận không tồn tại: " + commentId));

        // Kiểm tra quyền: chỉ chủ sở hữu mới được sửa
        if (!Objects.equals(comment.getUser().getUserId(), author.getUserId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa bình luận này.");
        }

        comment.setContent(request.content());
        RecipeComment savedComment = commentRepository.save(comment);

        // Cần tải lại các replies (nếu có) để trả về DTO hoàn chỉnh
        initializeRepliesRecursively(savedComment);
        return CommentResponse.from(savedComment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, User author) {
        RecipeComment comment = commentRepository.findByIdWithUser(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Bình luận không tồn tại: " + commentId));

        Long recipeOwnerId = comment.getRecipe().getUser() != null ? comment.getRecipe().getUser().getUserId() : null;

        // Kiểm tra quyền:
        // 1. Chủ sở hữu bình luận
        boolean isOwner = Objects.equals(comment.getUser().getUserId(), author.getUserId());
        // 2. Chủ sở hữu công thức
        boolean isRecipeOwner = Objects.equals(recipeOwnerId, author.getUserId());
        // 3. Admin
        boolean isAdmin = author.getRole() == Role.ADMIN;

        if (!isOwner && !isRecipeOwner && !isAdmin) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        // Xóa (CascadeType.ALL và orphanRemoval=true trên 'replies' sẽ xóa đệ quy)
        commentRepository.delete(comment);
    }
}