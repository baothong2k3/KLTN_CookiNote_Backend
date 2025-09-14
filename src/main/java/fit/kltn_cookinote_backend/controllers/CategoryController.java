/*
 * @ (#) CategoryController.java    1.0    07/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 07/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.CreateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.request.UpdateCategoryRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;
import fit.kltn_cookinote_backend.services.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService categoryService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest req, HttpServletRequest httpReq) {
        CategoryResponse data = categoryService.create(req);
        return ResponseEntity.ok(ApiResponse.success("Tạo danh mục thành công", data, httpReq.getRequestURI()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest req, HttpServletRequest httpReq) {
        CategoryResponse data = categoryService.update(id, req);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh mục thành công", data, httpReq.getRequestURI()));
    }

    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listAllCategories(HttpServletRequest httpReq) {
        List<CategoryResponse> data = categoryService.listAll();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục thành công", data, httpReq.getRequestURI()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listAllCategoriesByName(@RequestParam("categoryName") String categoryName, HttpServletRequest httpReq) {
        List<CategoryResponse> data = categoryService.listAllByCategoryName(categoryName);
        return ResponseEntity.ok(ApiResponse.success("Tìm danh mục thành công", data, httpReq.getRequestURI()));
    }
}
