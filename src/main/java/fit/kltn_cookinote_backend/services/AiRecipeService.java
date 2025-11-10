/*
 * @ (#) AiRecipeService.java    1.0    08/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 08/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.GenerateRecipeRequest;
import fit.kltn_cookinote_backend.dtos.response.GeneratedRecipeResponse;

/**
 * Service xử lý các nghiệp vụ liên quan đến AI tạo công thức.
 */
public interface AiRecipeService {

    /**
     * Yêu cầu AI tạo công thức từ tên món ăn.
     *
     * @param request Chứa tên món ăn.
     * @return DTO chứa dữ liệu công thức đã được AI tạo.
     * @throws RuntimeException nếu AI trả về lỗi hoặc JSON không hợp lệ.
     */
    GeneratedRecipeResponse generateRecipe(GenerateRecipeRequest request);
}
