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
import fit.kltn_cookinote_backend.dtos.request.UpdateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;
import fit.kltn_cookinote_backend.entities.Category;
import fit.kltn_cookinote_backend.repositories.CategoryRepository;
import fit.kltn_cookinote_backend.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

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

        return toResponse(saved);
    }

    @Override
    public CategoryResponse update(Long id, UpdateCategoryRequest req) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại"));
        String name = req.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Tên danh mục đã tồn tại");
        }
        category.setName(name);
        category.setDescription(req.description());

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Override
    public List<CategoryResponse> listAll() {
        List<Category> categories = categoryRepository.findAll(Sort.by("name").ascending());
        return categories.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> listAllByCategoryName(String categoryName) {
        String keyword = (categoryName == null) ? "" : categoryName.trim();
        if (!StringUtils.hasText(keyword)) {
            throw new IllegalArgumentException("Từ khoá tìm kiếm không được để trống");
        }
        List<Category> categories = categoryRepository.findByNameContainingIgnoreCase(keyword, Sort.by("name").ascending());
        return categories.stream().map(this::toResponse).toList();
    }

    private CategoryResponse toResponse(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .build();
    }
}
