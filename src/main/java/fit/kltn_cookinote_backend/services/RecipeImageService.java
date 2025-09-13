/*
 * @ (#) RecipeImageService.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface RecipeImageService {
    String uploadCover(Long recipeId, MultipartFile file) throws IOException;

    List<String> uploadStepImages(Long recipeId, Long stepId, List<MultipartFile> files) throws IOException;
}
