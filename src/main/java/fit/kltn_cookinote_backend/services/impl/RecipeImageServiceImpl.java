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
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepRepository;
import fit.kltn_cookinote_backend.services.RecipeImageService;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecipeImageServiceImpl implements RecipeImageService {
    private final Cloudinary cloudinary;
    private final RecipeRepository recipeRepository;
    private final RecipeStepRepository stepRepository;

    @Value("${app.cloudinary.recipe-folder}")
    private String recipeFolder;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    @Override
    public String uploadCover(Long recipeId, MultipartFile file) throws IOException {
        ImageValidationUtils.validateImage(file);
        return "";
    }

    @Override
    public List<String> uploadStepImages(Long recipeId, Long stepId, List<MultipartFile> files) throws IOException {
        files.forEach(ImageValidationUtils::validateImage);
        return List.of();
    }
}
