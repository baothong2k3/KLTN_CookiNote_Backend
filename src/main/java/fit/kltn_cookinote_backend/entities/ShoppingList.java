/*
 * @ (#) ShoppingList.java    1.0    17/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.entities;/*
 * @description:
 * @author: Bao Thong
 * @date: 17/08/2025
 * @version: 1.0
 */

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shopping_list")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoppingList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = true)
    private Recipe recipe;

    @Column(length = 100, nullable = false)
    private String ingredient;

    @Column(length = 50)
    private String quantity;

    @Builder.Default
    @Column
    private Boolean checked = false;

    @Column(length = 255)
    private String originalRecipeTitle; // Lưu tên recipe khi bị hard-delete

    /**
     * Đánh dấu item này được đồng bộ từ Recipe (true)
     * hay do người dùng tự thêm (false).
     */
    @Builder.Default
    @Column(name = "is_from_recipe", nullable = false)
    private Boolean isFromRecipe = false;

    @Builder.Default
    @Column(name = "is_recipe_deleted")
    private Boolean isRecipeDeleted = false; // Đánh dấu khi recipe bị soft-delete
}
