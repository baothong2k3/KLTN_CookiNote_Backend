/*
 * @ (#) RecipeController.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.controllers;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RecipeCreateRequest;
import fit.kltn_cookinote_backend.dtos.response.ApiResponse;
import fit.kltn_cookinote_backend.dtos.response.IdResponse;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.services.RecipeImageService;
import fit.kltn_cookinote_backend.services.RecipeService;
import fit.kltn_cookinote_backend.services.RecipeStepImageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/recipes")
public class RecipeController {
    private final RecipeService recipeService;
    private final RecipeImageService recipeImageService;
    private final RecipeStepImageService stepImageService;
    private final RecipeRepository recipeRepository;

    // PHA 1: Tạo recipe (USER/ADMIN; nếu PUBLIC chỉ ADMIN)
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<IdResponse>> createRecipe(@AuthenticationPrincipal User authUser,
                                                                @Valid @RequestBody RecipeCreateRequest req,
                                                                HttpServletRequest httpReq) {
        IdResponse data = recipeService.createByRecipe(authUser.getUserId(), req);
        return ResponseEntity.ok(ApiResponse.success("Tạo công thức thành công", data, httpReq.getRequestURI()));
    }

    // PHA 2a: Upload ảnh cover (owner hoặc ADMIN)
    @PostMapping(value = "/{recipeId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadCover(@AuthenticationPrincipal User authUser,
                                                           @PathVariable Long recipeId,
                                                           @RequestPart("file") MultipartFile file,
                                                           HttpServletRequest httpReq) throws IOException {
        String url = recipeImageService.uploadCover(authUser.getUserId(), recipeId, file);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh bìa thành công", url, httpReq.getRequestURI()));
    }

    // PHA 2b: Upload 1..5 ảnh cho một step (owner hoặc ADMIN)
    @PostMapping(value = "/{recipeId}/steps/{stepId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> uploadStepImages(@AuthenticationPrincipal User authUser,
                                                                      @PathVariable Long recipeId,
                                                                      @PathVariable Long stepId,
                                                                      @RequestPart("files") List<MultipartFile> files,
                                                                      HttpServletRequest httpReq) throws IOException {
        List<String> urls = stepImageService.addImagesToStep(authUser.getUserId(), recipeId, stepId, files);
        return ResponseEntity.ok(ApiResponse.success("Thêm ảnh bước thành công", urls, httpReq.getRequestURI()));
    }

}
