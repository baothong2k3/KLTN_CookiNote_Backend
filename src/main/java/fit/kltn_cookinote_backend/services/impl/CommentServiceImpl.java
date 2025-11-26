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
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.CommentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final RecipeRepository recipeRepository;
    private final RecipeCommentRepository commentRepository;
    private final UserRepository userRepository;

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

        boolean isAdmin = false;
        if (viewerId != null) {
            User viewer = userRepository.findById(viewerId).orElse(null);
            if (viewer != null && viewer.getRole() == Role.ADMIN) {
                isAdmin = true;
            }
        }

        Long ownerId = recipe.getUser() != null ? recipe.getUser().getUserId() : null;

        // Thêm !isAdmin vào điều kiện
        if (!isAdmin && !canView(recipe.getPrivacy(), ownerId, viewerId)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này.");
        }
        return recipe;
    }

    /**
     * Helper đệ quy để đếm số lượng replies (con cháu) của một bình luận.
     * Phải được gọi TRƯỚC KHI xóa bình luận cha.
     */
    private int countRepliesRecursively(RecipeComment comment) {
        // Tải collection replies một cách rõ ràng
        Hibernate.initialize(comment.getReplies());

        int count = 0;
        List<RecipeComment> replies = comment.getReplies();
        if (replies != null && !replies.isEmpty()) {
            count = replies.size(); // Đếm các con trực tiếp
            for (RecipeComment reply : replies) {
                count += countRepliesRecursively(reply); // Đếm các cháu (đệ quy)
            }
        }
        return count;
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

        if (parent != null) {
            Hibernate.initialize(parent.getReplies());
            parent.getReplies().add(newComment);
        }

        // 4. Lưu bình luận mới
        RecipeComment savedComment = commentRepository.save(newComment);

        // 5. Cập nhật bộ đếm (Tăng 1)
        int currentCount = recipe.getCommentCount() != null ? recipe.getCommentCount() : 0;
        recipe.setCommentCount(currentCount + 1);
        recipeRepository.save(recipe); // Lưu recipe

        // 6. Trả về DTO
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
        comment.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
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

        Recipe recipe = comment.getRecipe(); // Lấy recipe TRƯỚC KHI XÓA

        // Đếm số lượng bình luận sẽ bị xóa (chính nó + con cháu)
        // Phải tải đệ quy tất cả replies TRƯỚC KHI xóa
        int totalToDelete = 1 + countRepliesRecursively(comment);

        Long recipeOwnerId = recipe.getUser() != null ? recipe.getUser().getUserId() : null;

        // ... (Kiểm tra quyền)
        boolean isOwner = Objects.equals(comment.getUser().getUserId(), author.getUserId());
        boolean isRecipeOwner = Objects.equals(recipeOwnerId, author.getUserId());
        boolean isAdmin = author.getRole() == Role.ADMIN;

        if (!isOwner && !isRecipeOwner && !isAdmin) {
            throw new AccessDeniedException("Bạn không có quyền xóa bình luận này.");
        }

        // Xóa (CascadeType.ALL và orphanRemoval=true trên 'replies' sẽ xóa đệ quy)
        commentRepository.delete(comment);
        commentRepository.flush(); // Đảm bảo xóa xong

        // Cập nhật bộ đếm (Giảm đi)
        int currentCount = recipe.getCommentCount() != null ? recipe.getCommentCount() : 0;
        recipe.setCommentCount(Math.max(0, currentCount - totalToDelete));
        recipeRepository.save(recipe);
    }
}