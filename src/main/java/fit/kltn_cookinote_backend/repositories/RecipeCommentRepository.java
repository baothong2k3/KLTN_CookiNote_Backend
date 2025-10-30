/*
 * @ (#) RecipeCommentRepository.java    1.0    30/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.repositories;/*
 * @description:
 * @author: Bao Thong
 * @date: 30/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeCommentRepository extends JpaRepository<RecipeComment, Long> {

    /**
     * Tìm tất cả bình luận gốc (parent is null) cho một công thức,
     * sắp xếp theo thời gian tạo cũ nhất trước (để hiển thị đúng thứ tự).
     * Sử dụng JOIN FETCH để tải luôn thông tin User (tác giả).
     */
    @Query("SELECT c FROM RecipeComment c LEFT JOIN FETCH c.user WHERE c.recipe.id = :recipeId AND c.parent IS NULL ORDER BY c.createdAt ASC")
    List<RecipeComment> findTopLevelCommentsByRecipeId(@Param("recipeId") Long recipeId);

    /**
     * Tải một bình luận và tất cả các trả lời của nó (một cách đệ quy)
     * Đây là một truy vấn phức tạp, chúng ta sẽ tải bình luận gốc
     * và để Hibernate tải các 'replies' khi cần (hoặc dùng @EntityGraph nếu muốn tối ưu).
     * Tạm thời, chúng ta chỉ cần tải bình luận và user.
     */
    @Query("SELECT c FROM RecipeComment c LEFT JOIN FETCH c.user WHERE c.id = :id")
    Optional<RecipeComment> findByIdWithUser(@Param("id") Long id);
}
