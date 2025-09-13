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

import java.util.List;

@Entity
@Table(name = "recipe_step")
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

    @Column(name = "step_no")
    private Integer stepNo;

    @Column(length = 4096)
    private String content;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RecipeStepImage> images;
}

