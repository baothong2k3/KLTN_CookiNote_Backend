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

import fit.kltn_cookinote_backend.dtos.request.RecipeStepUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface RecipeStepImageService {
    List<String> addImagesToStep(Long actorUserId, Long recipeId, Long stepId, List<MultipartFile> files) throws IOException;

    RecipeResponse updateStep(Long actorUserId, Long recipeId, Long stepId, RecipeStepUpdateRequest req) throws IOException;

    /**
     * Thêm một bước mới (step) vào cuối một công thức (recipe) đã tồn tại.
     * Chỉ chủ sở hữu hoặc ADMIN mới có quyền.
     *
     * @param actorUserId   ID người thực hiện
     * @param recipeId      ID của công thức
     * @param content       Nội dung bước
     * @param suggestedTime Thời gian gợi ý (optional)
     * @param tips          Mẹo (optional)
     * @param addFiles      Danh sách ảnh (optional)
     * @return RecipeResponse đã được cập nhật
     * @throws IOException
     */
    RecipeResponse addStep(Long actorUserId, Long recipeId, String content, Integer suggestedTime, String tips, List<MultipartFile> addFiles) throws IOException;
}
