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

import fit.kltn_cookinote_backend.dtos.request.MoveRecipesRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.CategoryResponse;
import fit.kltn_cookinote_backend.services.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService categoryService;

    // CREATE: name, [description], [image]
    @PostMapping(value = {"/create"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestPart("name") String name,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "image", required = false) MultipartFile image,
            HttpServletRequest req) throws IOException {

        var res = categoryService.createWithOptionalImage(name, description, image);
        return ResponseEntity.ok(ApiResponse.success("Tạo danh mục thành công", res, req.getRequestURI()));
    }

    // UPDATE: name, [description], [image]
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @RequestPart(value = "name", required = false) String name,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "image", required = false) MultipartFile image,
            HttpServletRequest req) throws IOException {

        var res = categoryService.updateWithOptionalImage(id, name, description, image);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh mục thành công", res, req.getRequestURI()));
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

    @PostMapping("/move-recipes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> moveRecipes(
            @Valid @RequestBody MoveRecipesRequest req,
            HttpServletRequest httpReq) {
        Map<String, Integer> result = categoryService.moveRecipes(req);
        String message = String.format("Đã chuyển %d công thức thành công.", result.get("movedCount"));
        return ResponseEntity.ok(ApiResponse.success(message, result, httpReq.getRequestURI()));
    }
}
