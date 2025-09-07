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

import fit.kltn_cookinote_backend.dtos.request.CreateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.request.UpdateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CreateCategoryRequest req);
    CategoryResponse update(Long id, UpdateCategoryRequest req);
    List<CategoryResponse> listAll();
}