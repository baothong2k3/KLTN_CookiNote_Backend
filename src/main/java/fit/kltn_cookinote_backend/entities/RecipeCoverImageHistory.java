/*
 * @ (#) RecipeCoverImageHistory.java    1.0    11/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.entities;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/10/2025
 * @version: 1.0
 */

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recipe_cover_image_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeCoverImageHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "image_url", length = 2048, nullable = false)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
