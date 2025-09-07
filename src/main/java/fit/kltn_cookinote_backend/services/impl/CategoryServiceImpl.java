/*
 * @ (#) CategoryServiceImpl.java    1.0    07/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 07/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.CreateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;
import fit.kltn_cookinote_backend.entities.Category;
import fit.kltn_cookinote_backend.repositories.CategoryRepository;
import fit.kltn_cookinote_backend.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryResponse create(CreateCategoryRequest req) {
        String name = req.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Tên danh mục đã tồn tại");
        }

        Category saved = categoryRepository.save(
                Category.builder()
                        .name(name)
                        .description(req.description())
                        .build()
        );

        return CategoryResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .description(saved.getDescription())
                .build();
    }
}
