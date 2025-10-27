/*
 * @ (#) CookedHistory.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.entities;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cooked_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookedHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = true) // Cho phép null nếu recipe bị xóa
    private Recipe recipe;

    @Column(name = "cooked_at", nullable = false, updatable = false)
    @CreationTimestamp // Tự động set thời gian khi tạo
    private LocalDateTime cookedAt;

    // Lưu lại tên gốc phòng khi Recipe bị xóa vĩnh viễn
    @Column(name = "original_recipe_title", length = 255)
    private String originalRecipeTitle;

    // Đánh dấu nếu Recipe tương ứng đã bị xóa (mềm hoặc cứng)
    @Column(name = "is_recipe_deleted")
    @Builder.Default
    private Boolean isRecipeDeleted = false;
}
