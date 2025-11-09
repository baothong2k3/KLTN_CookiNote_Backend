/*
 * @ (#) DailyMenu.java    1.0    17/08/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.entities;/*
 * @description:
 * @author: Bao Thong
 * @date: 17/08/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.enums.MealType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_menu", indexes = {
        @Index(name = "idx_daily_menu_user_date", columnList = "user_id, menu_date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyMenu {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 20)
    private MealType mealType;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;


    @Column(name = "anchor_source", length = 50)
    private String anchorSource;

    @Column(name = "strategy", length = 255)
    private String strategy;
}