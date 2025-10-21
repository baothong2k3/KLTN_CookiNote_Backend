/*
 * @ (#) RecipeImageService.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import com.cloudinary.Cloudinary;
import fit.kltn_cookinote_backend.dtos.response.AllRecipeImagesResponse;
import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.RecipeCoverImageHistoryRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.RecipeImageService;
import fit.kltn_cookinote_backend.utils.CloudinaryUtils;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeImageServiceImpl implements RecipeImageService {
    private final Cloudinary cloudinary;
    private final RecipeRepository recipeRepository;
    private final RecipeStepRepository stepRepository;
    private final UserRepository userRepository;
    private final RecipeCoverImageHistoryRepository recipeCoverImageHistoryRepository;

    @Value("${app.cloudinary.recipe-folder}")
    private String recipeFolder;

    /**
     * Centralized logic to handle cover image validation, upload, and persistence.
     *
     * @param actorUserId The user performing the action.
     * @param recipeId    The recipe to update.
     * @param file        The new cover image file.
     * @return The updated Recipe entity.
     * @throws IOException If the file upload fails.
     */
    private Recipe processCoverUpdate(Long actorUserId, Long recipeId, MultipartFile file) throws IOException {
        ImageValidationUtils.validateImage(file);

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Công thức không tồn tại: " + recipeId));

        // KIỂM TRA TRẠNG THÁI DELETED
        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Công thức không tồn tại hoặc đã bị xóa: " + recipeId);
        }
        // Consolidated ownership check
        if (actor.getRole() != Role.ADMIN && !Objects.equals(actor.getUserId(), recipe.getUser().getUserId())) {
            throw new AccessDeniedException("Chỉ chủ sở hữu hoặc ADMIN mới được chỉnh sửa.");
        }

        String publicId = recipeFolder + "/r_" + recipeId + "/cover_" + Instant.now().getEpochSecond();
        String newUrl = CloudinaryUtils.uploadImage(cloudinary, file, recipeFolder, publicId);

        recipeCoverImageHistoryRepository.deactivateAllByRecipeId(recipe.getId());

        recipe.setImageUrl(newUrl);

        RecipeCoverImageHistory historyRecord = RecipeCoverImageHistory.builder()
                .recipe(recipe)
                .imageUrl(newUrl)
                .build();
        recipe.getCoverImageHistory().add(historyRecord);

        return recipeRepository.saveAndFlush(recipe);
    }

    @Override
    @Transactional
    public String uploadCover(Long actorUserId, Long recipeId, MultipartFile file) throws IOException {
        Recipe updatedRecipe = processCoverUpdate(actorUserId, recipeId, file);
        return updatedRecipe.getImageUrl();
    }

    @Override
    @Transactional
    public RecipeResponse updateCover(Long actorUserId, Long recipeId, MultipartFile file) throws IOException {
        Recipe updatedRecipe = processCoverUpdate(actorUserId, recipeId, file);
        return RecipeResponse.from(updatedRecipe);
    }

    @Override
    public List<String> uploadStepImages(Long recipeId, Long stepId, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty())
            throw new IllegalArgumentException("Danh sách ảnh trống.");

        RecipeStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Step không tồn tại: " + stepId));

        // *** KIỂM TRA RECIPE CỦA STEP ***
        if (step.getRecipe().isDeleted()) {
            throw new EntityNotFoundException("Công thức của bước này đã bị xóa.");
        }

        if (!Objects.equals(step.getRecipe().getId(), recipeId)) {
            throw new IllegalArgumentException("Step không thuộc về Recipe này.");
        }

        // kiểm tra số lượng ảnh tối đa 5
        long current = step.getImages() == null ? 0 : step.getImages().size();
        if (current + files.size() > 5)
            throw new IllegalArgumentException("Mỗi Step chỉ chứa tối đa 5 ảnh.");

        List<String> urls = new ArrayList<>();
        int i = 1;
        for (MultipartFile f : files) {
            ImageValidationUtils.validateImage(f);

            String publicId = recipeFolder + "/r_" + recipeId + "/s_" + stepId + "/img_" + Instant.now().getEpochSecond() + "_" + (i++);
            String url = CloudinaryUtils.uploadImage(cloudinary, f, recipeFolder, publicId);
            urls.add(url);
        }

        return urls;
    }

    @Override
    @Transactional(readOnly = true)
    public AllRecipeImagesResponse getAllRecipeImages(Long actorUserId, Long recipeId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Công thức không tồn tại: " + recipeId));

        if (actor.getRole() != Role.ADMIN && !Objects.equals(actor.getUserId(), recipe.getUser().getUserId())) {
            throw new AccessDeniedException("Chỉ chủ sở hữu hoặc ADMIN mới có quyền xem lịch sử ảnh.");
        }

        List<RecipeCoverImageHistory> coverHistories = recipeCoverImageHistoryRepository.findByRecipe_Id(recipeId);

        List<RecipeStepImage> stepImages = recipe.getSteps().stream()
                .flatMap(step -> step.getImages().stream())
                .collect(Collectors.toList());

        return AllRecipeImagesResponse.from(coverHistories, stepImages);
    }
}
