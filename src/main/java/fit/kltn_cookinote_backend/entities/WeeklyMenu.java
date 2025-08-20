/*
 * @ (#) WeeklyMenu.java    1.0    17/08/2025
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

@Entity
@Table(name = "weekly_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Integer week;

    @Column(name = "day_of_week")
    private Integer dayOfWeek; // 1..7

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 20)
    private MealType mealType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 4096)
    private String note;
}
