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
import fit.kltn_cookinote_backend.dtos.request.RecipeStepUpdateRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.User;
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

    // PHA 1: Tạo recipe (USER/ADMIN; nếu PUBLIC chỉ ADMIN)
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(@AuthenticationPrincipal User authUser,
                                                                    @Valid @RequestBody RecipeCreateRequest req,
                                                                    HttpServletRequest httpReq) {
        RecipeResponse data = recipeService.createByRecipe(authUser.getUserId(), req);
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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RecipeResponse>> getDetail(@AuthenticationPrincipal User authUser,
                                                                 @PathVariable Long id,
                                                                 HttpServletRequest httpReq) {
        Long viewerId = (authUser != null) ? authUser.getUserId() : null;
        RecipeResponse data = recipeService.getDetail(viewerId, id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết công thức thành công", data, httpReq.getRequestURI()));
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> listByCategory(
            @PathVariable Long categoryId,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        PageResult<RecipeCardResponse> data = recipeService.listPublicByCategory(categoryId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức theo danh mục thành công", data, httpReq.getRequestURI()));
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> listPublic(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        PageResult<RecipeCardResponse> data = recipeService.listPublic(page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức công khai thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Danh sách recipe của một user bất kỳ:
     * - Nếu viewer == owner ⇒ trả PRIVATE + SHARED + PUBLIC
     * - Nếu viewer != owner hoặc ẩn danh ⇒ trả SHARED + PUBLIC
     * GET /recipes/users/{ownerId}?page=0&size=12
     */
    @GetMapping("/users/{ownerId}")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> listByOwner(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long ownerId,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        Long viewerId = (authUser != null) ? authUser.getUserId() : null;
        PageResult<RecipeCardResponse> data = recipeService.listByOwner(ownerId, viewerId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức theo chủ sở hữu thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Tiện ích: danh sách của chính tôi (bao gồm PRIVATE/SHARED/ PUBLIC của bản thân).
     * GET /recipes/me?page=0&size=12
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> listMine(
            @AuthenticationPrincipal User authUser,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        PageResult<RecipeCardResponse> data = recipeService.listByOwner(authUser.getUserId(), authUser.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức của tôi thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Lấy danh sách bước của 1 recipe (tôn trọng privacy).
     * GET /recipes/{recipeId}/steps
     */
    @GetMapping("/{recipeId}/steps")
    public ResponseEntity<ApiResponse<List<RecipeStepItem>>> getSteps(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        Long viewerId = (authUser != null) ? authUser.getUserId() : null;
        List<RecipeStepItem> data = recipeService.getSteps(viewerId, recipeId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bước thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Lấy danh sách nguyên liệu của 1 recipe (tôn trọng privacy).
     * GET /recipes/{recipeId}/ingredients
     */
    @GetMapping("/{recipeId}/ingredients")
    public ResponseEntity<ApiResponse<List<RecipeIngredientItem>>> getIngredients(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        Long viewerId = (authUser != null) ? authUser.getUserId() : null;
        List<RecipeIngredientItem> data = recipeService.getIngredients(viewerId, recipeId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nguyên liệu thành công", data, httpReq.getRequestURI()));
    }

    @PutMapping("/{recipeId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> updateContent(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @Valid @RequestBody RecipeUpdateRequest req,
            HttpServletRequest httpReq
    ) {
        RecipeResponse data = recipeService.updateContent(authUser.getUserId(), recipeId, req);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nội dung công thức thành công", data, httpReq.getRequestURI()));
    }

    @PutMapping(value = "/{recipeId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> updateCover(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest httpReq
    ) throws IOException {
        RecipeResponse data = recipeImageService.updateCover(authUser.getUserId(), recipeId, file);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh bìa thành công", data, httpReq.getRequestURI()));
    }

    @PutMapping(value = "/{recipeId}/steps/{stepId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> updateStep(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @PathVariable Long stepId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "stepNo", required = false) Integer stepNo,
            @RequestParam(value = "suggestTime", required = false) Integer suggestedTime,
            @RequestParam(value = "tips", required = false) String tips,
            @RequestParam(value = "keepUrls", required = false) List<String> keepUrls,
            @RequestPart(value = "addFiles", required = false) List<MultipartFile> addFiles,
            HttpServletRequest httpReq
    ) throws IOException {
        RecipeStepUpdateRequest req = new RecipeStepUpdateRequest(content, stepNo, suggestedTime, tips, keepUrls, addFiles);
        RecipeResponse data = stepImageService.updateStep(authUser.getUserId(), recipeId, stepId, req);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bước công thức thành công", data, httpReq.getRequestURI()));
    }
}
