/*
 * @ (#) CategoryService.java    1.0    07/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 07/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;
import jakarta.annotation.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface CategoryService {

    List<CategoryResponse> listAll();

    List<CategoryResponse> listAllByCategoryName(String categoryName);

    CategoryResponse createWithOptionalImage(String name, String description, @Nullable MultipartFile image) throws IOException;

    CategoryResponse updateWithOptionalImage(Long id, String name, String description, @Nullable MultipartFile image) throws IOException;
}