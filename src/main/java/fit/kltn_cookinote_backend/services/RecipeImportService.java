/*
 * @ (#) RecipeImportService.java    1.0    16/11/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 16/11/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.GeneratedRecipeResponse;

import java.io.IOException;

/**
 * Service xử lý nghiệp vụ nhập (cào) công thức từ URL bên ngoài.
 */
public interface RecipeImportService {

    /**
     * Thử cào dữ liệu công thức từ một URL công khai.
     *
     * @param url URL của trang web công thức
     * @return DTO GeneratedRecipeResponse chứa dữ liệu cào được (best-effort)
     * @throws IOException nếu không thể kết nối hoặc đọc URL
     */
    GeneratedRecipeResponse importRecipeFromUrl(String url) throws IOException;
}
