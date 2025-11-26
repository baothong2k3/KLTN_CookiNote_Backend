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
import fit.kltn_cookinote_backend.dtos.request.MoveRecipesRequest;
import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;
import fit.kltn_cookinote_backend.entities.Category;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.repositories.CategoryRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.services.CategoryService;
import fit.kltn_cookinote_backend.services.CloudinaryService;
import fit.kltn_cookinote_backend.utils.CloudinaryUtils;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final RecipeRepository recipeRepository;
    private final CloudinaryService cloudinaryService;
    private final Cloudinary cloudinary;

    @Value("${app.cloudinary.category-folder}")
    private String categoryFolder;

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
            uploadAndSetImage(cat, image);
            categoryRepository.save(cat);
        }
        return toResponse(cat);
    }

    @Override
    @Transactional
    public CategoryResponse updateWithOptionalImage(Long id, String nameRaw, String description, MultipartFile image) throws IOException {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại"));

        String name = validateAndNormalizeName(nameRaw);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Tên danh mục đã tồn tại");
        }

        cat.setName(name);
        cat.setDescription(description);

        final String oldUrl = cat.getImageUrl();

        if (image != null && !image.isEmpty()) {
            // Upload ảnh mới
            uploadAndSetImage(cat, image);

            // Xóa ảnh cũ sau khi commit thành công (Sử dụng Helper Method)
            deleteImageAfterCommit(oldUrl);
        }

        Category saved = categoryRepository.save(cat);
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

    @Override
    @Transactional
    public Map<String, Integer> moveRecipes(MoveRecipesRequest req) {
        Long sourceId = req.sourceCategoryId();
        Long destId = req.destinationCategoryId();

        if (Objects.equals(sourceId, destId)) {
            throw new IllegalArgumentException("Danh mục nguồn và đích không được trùng nhau.");
        }

        if (!categoryRepository.existsById(sourceId)) {
            throw new IllegalArgumentException("Danh mục nguồn không tồn tại: " + sourceId);
        }
        if (!categoryRepository.existsById(destId)) {
            throw new IllegalArgumentException("Danh mục đích không tồn tại: " + destId);
        }

        int movedCount;
        if (req.recipeIds() == null || req.recipeIds().isEmpty()) {
            // Chuyển tất cả
            movedCount = recipeRepository.moveAllRecipesByCategory(sourceId, destId);
        } else {
            // Chuyển theo danh sách ID
            movedCount = recipeRepository.moveRecipesByIds(sourceId, destId, req.recipeIds());
        }

        return Map.of("movedCount", movedCount);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        // 1. Tìm Category
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Danh mục không tồn tại: " + id));

        // 2. Kiểm tra ràng buộc và đếm chi tiết
        List<Recipe> recipes = category.getRecipes();
        if (recipes != null && !recipes.isEmpty()) {
            // Đếm số lượng công thức theo trạng thái
            long activeCount = recipes.stream().filter(r -> !r.isDeleted()).count();
            long softDeletedCount = recipes.stream().filter(Recipe::isDeleted).count();

            // Tạo thông báo chi tiết
            throw new IllegalStateException(String.format(
                    "Không thể xóa danh mục này vì còn chứa %d công thức (%d đang hoạt động, %d đã xóa mềm). " +
                            "Vui lòng chuyển các công thức này sang danh mục khác trước khi xóa.",
                    recipes.size(), activeCount, softDeletedCount
            ));
        }

        // 3. Lấy URL ảnh để chuẩn bị xóa (nếu có)
        final String imageUrl = category.getImageUrl();

        // 4. Xóa Category khỏi DB
        categoryRepository.delete(category);

        // 5. Xóa ảnh sau khi commit thành công (Sử dụng Helper Method)
        deleteImageAfterCommit(imageUrl);
    }

    private CategoryResponse toResponse(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .imageUrl(c.getImageUrl())
                .build();
    }

    // ---- helpers ----

    private String validateAndNormalizeName(String raw) {
        if (!StringUtils.hasText(raw)) throw new IllegalArgumentException("Tên danh mục không được để trống");
        String name = raw.trim();
        if (name.length() > 100) throw new IllegalArgumentException("Tên danh mục tối đa 100 ký tự");
        return name;
    }

    /**
     * Helper: Validate ảnh, tạo publicId, upload lên Cloudinary và set URL cho Category.
     */
    private void uploadAndSetImage(Category cat, MultipartFile image) throws IOException {
        ImageValidationUtils.validateImage(image);
        String publicId = "c_" + cat.getId() + "_" + Instant.now().getEpochSecond();
        String imageUrl = CloudinaryUtils.uploadImage(cloudinary, image, categoryFolder, publicId);
        cat.setImageUrl(imageUrl);
    }

    /**
     * Helper: Đăng ký transaction synchronization để xóa ảnh trên Cloudinary
     * chỉ sau khi transaction DB commit thành công.
     */
    private void deleteImageAfterCommit(String imageUrl) {
        if (StringUtils.hasText(imageUrl)) {
            String publicId = cloudinaryService.extractPublicIdFromUrl(imageUrl);
            if (StringUtils.hasText(publicId)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cloudinaryService.safeDeleteByPublicId(publicId);
                    }
                });
            }
        }
    }
}