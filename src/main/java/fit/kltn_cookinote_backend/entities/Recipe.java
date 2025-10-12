/*
 * @ (#) Recipe.java    1.0    17/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.entities;/*
 * @description:
 * @author: Bao Thong
 * @date: 17/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.Difficulty;
import fit.kltn_cookinote_backend.enums.Privacy;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "recipe", indexes = {
        @Index(name = "idx_recipe_title", columnList = "title")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // owner
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // category
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(length = 2048)
    private String description;

    @Column(name = "prepare_time")
    private Integer prepareTime;

    @Column(name = "cook_time")
    private Integer cookTime;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    private Privacy privacy;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column
    private Long view;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // children
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo ASC")
    @Builder.Default
    @Fetch(FetchMode.SUBSELECT)
    private List<RecipeStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @Fetch(FetchMode.SUBSELECT)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Favorite> favorites = new HashSet<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Share> shares = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShoppingList> shoppingLists = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecipeCoverImageHistory> coverImageHistory = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WeeklyMenu> weeklyMenus = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DailyMenu> dailyMenus = new ArrayList<>();
}
