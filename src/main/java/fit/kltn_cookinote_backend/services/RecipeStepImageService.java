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

import fit.kltn_cookinote_backend.dtos.request.DeleteRecipeStepsRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeStepReorderRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeStepUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    /**
     * Sắp xếp lại thứ tự (stepNo) của các bước trong một công thức.
     * Chỉ chủ sở hữu hoặc ADMIN mới có quyền.
     * Yêu cầu request phải chứa tất cả các step hiện có và stepNo mới phải hợp lệ.
     *
     * @param actorUserId ID người thực hiện
     * @param recipeId    ID công thức
     * @param req         Đối tượng chứa danh sách thứ tự mới
     * @return RecipeResponse đã được cập nhật
     */
    RecipeResponse reorderSteps(Long actorUserId, Long recipeId, RecipeStepReorderRequest req);

    /**
     * Xóa một hoặc nhiều bước (step) khỏi một công thức.
     * Khi xóa step, tất cả ảnh liên quan cũng sẽ bị xóa khỏi DB và Cloudinary.
     * Các bước còn lại sẽ được tự động đánh số lại thứ tự.
     * Chỉ chủ sở hữu hoặc ADMIN mới có quyền.
     *
     * @param actorUserId ID người thực hiện
     * @param recipeId    ID công thức
     * @param req         Đối tượng chứa danh sách ID các bước cần xóa
     * @return Map chứa số lượng bước đã xóa ("deletedCount") và số bước đã sắp xếp lại ("reorderedCount")
     */
    Map<String, Integer> deleteSteps(Long actorUserId, Long recipeId, DeleteRecipeStepsRequest req);
}
