/*
 * @ (#) RecipeStepImageServiceImpl.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeStep;
import fit.kltn_cookinote_backend.entities.RecipeStepImage;
import fit.kltn_cookinote_backend.repositories.RecipeStepImageRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepRepository;
import fit.kltn_cookinote_backend.services.RecipeImageService;
import fit.kltn_cookinote_backend.services.RecipeStepImageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RecipeStepImageServiceImpl implements RecipeStepImageService {

    private final RecipeImageService recipeImageService;
    private final RecipeStepRepository stepRepository;
    private final RecipeStepImageRepository stepImageRepository;

    @Override
    @Transactional
    public List<String> addImagesToStep(Long recipeId, Long stepId, List<MultipartFile> files) throws IOException {
        RecipeStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Step không tồn tại: " + stepId));

        if (!Objects.equals(step.getRecipe().getId(), recipeId)) {
            throw new IllegalArgumentException("Step không thuộc về Recipe này.");
        }

        // Upload
        List<String> urls = recipeImageService.uploadStepImages(recipeId, stepId, files);

        // Persist
        List<RecipeStepImage> toSave = new ArrayList<>();
        for (String url : urls) {
            RecipeStepImage img = RecipeStepImage.builder()
                    .step(step)
                    .imageUrl(url)
                    .build();
            toSave.add(img);
        }
        stepImageRepository.saveAll(toSave);

        // đồng bộ list trong entity (nếu có mapping)
        if (step.getImages() != null) {
            step.getImages().addAll(toSave);
        }

        return urls;
    }
}
