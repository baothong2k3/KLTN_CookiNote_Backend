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
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeStep;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Role;
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

@Service
@RequiredArgsConstructor
public class RecipeImageServiceImpl implements RecipeImageService {
    private final Cloudinary cloudinary;
    private final RecipeRepository recipeRepository;
    private final RecipeStepRepository stepRepository;
    private final UserRepository userRepository;

    @Value("${app.cloudinary.recipe-folder}")
    private String recipeFolder;

    private void ensureOwnerOrAdmin(Long actorId, Long ownerId, Role actorRole) {
        if (actorRole == Role.ADMIN) return;
        if (!actorId.equals(ownerId)) throw new AccessDeniedException("Chỉ chủ sở hữu hoặc ADMIN mới được chỉnh sửa.");
    }

    @Override
    @Transactional
    public String uploadCover(Long actorUserId, Long recipeId, MultipartFile file) throws IOException {
        ImageValidationUtils.validateImage(file);

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Long ownerId = recipeRepository.findOwnerId(recipeId);

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        String publicId = recipeFolder + "/r_" + recipeId + "/cover_" + Instant.now().getEpochSecond();
        String url = CloudinaryUtils.uploadImage(cloudinary, file, recipeFolder, publicId);

        recipe.setImageUrl(url);
        recipeRepository.save(recipe);

        return url;
    }

    @Override
    public List<String> uploadStepImages(Long recipeId, Long stepId, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty())
            throw new IllegalArgumentException("Danh sách ảnh trống.");

        RecipeStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Step không tồn tại: " + stepId));

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
}
