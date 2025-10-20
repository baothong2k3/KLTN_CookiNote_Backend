/*
 * @ (#) RecipeStep.java    1.0    17/08/2025
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
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.List;

@Entity
@Table(name = "recipe_step",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recipe_step_recipe_stepno",
                columnNames = {"recipe_id", "step_no"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(length = 4096)
    private String content;

    @Column(name = "suggested_time") // Thời gian gợi ý (phút)
    private Integer suggestedTime;

    @Column(length = 2048) // Mẹo/ghi chú cho bước này
    private String tips;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<RecipeStepImage> images;
}

