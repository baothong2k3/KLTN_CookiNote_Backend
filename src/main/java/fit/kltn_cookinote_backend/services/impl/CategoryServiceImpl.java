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

import com.cloudinary.Cloudinary;
import fit.kltn_cookinote_backend.dtos.request.CreateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.request.UpdateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;
import fit.kltn_cookinote_backend.entities.Category;
import fit.kltn_cookinote_backend.repositories.CategoryRepository;
import fit.kltn_cookinote_backend.services.CategoryService;
import fit.kltn_cookinote_backend.utils.CloudinaryUtils;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final Cloudinary cloudinary;

    @Value("${app.cloudinary.category-folder}")
    private String categoryFolder;

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
    @Transactional
    public CategoryResponse createWithOptionalImage(String nameRaw, String description, @Nullable MultipartFile image) throws IOException {
        String name = validateAndNormalizeName(nameRaw);
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Tên danh mục đã tồn tại");
        }

        // 1) tạo trước để có id
        Category cat = Category.builder()
                .name(name)
                .description(description)
                .build();
        cat = categoryRepository.saveAndFlush(cat);

        // 2) nếu có ảnh → upload và lưu URL
        if (image != null && !image.isEmpty()) {
            ImageValidationUtils.validateImage(image);
            String publicId = "c_" + cat.getId() + "_" + Instant.now().getEpochSecond();
            String imageUrl = CloudinaryUtils.uploadImage(cloudinary, image, categoryFolder, publicId);
            cat.setImageUrl(imageUrl);
            categoryRepository.save(cat);
        }
        return toResponse(cat);
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
                .imageUrl(c.getImageUrl())
                .build();
    }

    private String validateAndNormalizeName(String raw) {
        if (!StringUtils.hasText(raw)) throw new IllegalArgumentException("Tên danh mục không được để trống");
        String name = raw.trim();
        if (name.length() > 100) throw new IllegalArgumentException("Tên danh mục tối đa 100 ký tự");
        return name;
    }
}
