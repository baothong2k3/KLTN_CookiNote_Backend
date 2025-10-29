/*
 * @ (#) ImageService.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.User;

import java.util.List;
import java.util.Map;

/**
 * Service xử lý các nghiệp vụ liên quan đến ảnh (xóa, ...).
 */
public interface ImageService {

    /**
     * Xóa vĩnh viễn các ảnh đã ở trạng thái 'active: false'.
     * Yêu cầu quyền admin hoặc chủ sở hữu.
     *
     * @param actor         Người thực hiện
     * @param coverImageIds Danh sách ID ảnh bìa (RecipeCoverImageHistory)
     * @param stepImageIds  Danh sách ID ảnh bước (RecipeStepImage)
     * @return Map chứa số lượng ảnh đã xóa
     */
    Map<String, Integer> deleteInactiveImages(User actor, List<Long> coverImageIds, List<Long> stepImageIds);
}
