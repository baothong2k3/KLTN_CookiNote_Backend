/*
 * @ (#) RecipeStepImageService.java    1.0    13/09/2025
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

public interface RecipeStepImageService {
    List<String> addImagesToStep(Long actorUserId, Long recipeId, Long stepId, List<MultipartFile> files) throws IOException;
}
