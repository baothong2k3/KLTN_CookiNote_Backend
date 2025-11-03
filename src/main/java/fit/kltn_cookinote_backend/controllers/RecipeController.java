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

import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.repositories.FavoriteRepository;
import fit.kltn_cookinote_backend.services.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/recipes")
public class RecipeController {
    private final RecipeService recipeService;
    private final RecipeImageService recipeImageService;
    private final RecipeStepImageService stepImageService;
    private final FavoriteService favoriteService;
    private final ShareService shareService;
    private final FavoriteRepository favoriteRepository;

    @PostMapping(value = "/create-with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> createRecipeFull(
            @AuthenticationPrincipal User authUser,
            @RequestPart("recipe") String recipeJson, // Pha 1: JSON data
            @RequestPart(value = "cover", required = false) MultipartFile coverImage, // Pha 2a: Cover
            MultipartHttpServletRequest httpReq // Dùng để lấy các part ảnh step (Pha 2b)
    ) throws IOException {

        // Lọc ra các file ảnh của steps (Pha 2b)
        // Lấy tất cả các file parts có key bắt đầu bằng "stepImages_"
        Map<String, List<MultipartFile>> stepImagesMap = httpReq.getMultiFileMap().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("stepImages_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Gọi service mới
        RecipeResponse data = recipeService.createRecipeFull(
                authUser.getUserId(),
                recipeJson,
                coverImage,
                stepImagesMap
        );

        return ResponseEntity.ok(ApiResponse.success("Tạo công thức thành công", data, httpReq.getRequestURI()));
    }

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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RecipeResponse>> getDetail(@AuthenticationPrincipal User authUser,
                                                                 @PathVariable Long id,
                                                                 HttpServletRequest httpReq) {
        Long viewerId = authUser.getUserId();
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

    /**
     * Thêm một công thức vào danh sách yêu thích của người dùng hiện tại.
     * POST /recipes/{recipeId}/favorite
     */
    @PostMapping("/{recipeId}/favorite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> addFavorite(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        favoriteService.addRecipeToFavorites(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm công thức vào danh sách yêu thích", httpReq.getRequestURI()));
    }

    /**
     * Lấy danh sách các công thức yêu thích của người dùng hiện tại.
     * GET /recipes/me/favorites
     */
    @GetMapping("/me/favorites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RecipeCardResponse>>> getMyFavorites(
            @AuthenticationPrincipal User authUser,
            HttpServletRequest httpReq
    ) {
        List<RecipeCardResponse> data = favoriteService.getFavoriteRecipes(authUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức yêu thích thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Xóa một công thức khỏi danh sách yêu thích của người dùng hiện tại.
     * DELETE /recipes/{recipeId}/favorite
     */
    @DeleteMapping("/{recipeId}/favorite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        favoriteService.removeRecipeFromFavorites(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa công thức khỏi danh sách yêu thích", httpReq.getRequestURI()));
    }

    /**
     * Xóa một công thức (owner hoặc ADMIN).
     *
     * @param authUser
     * @param recipeId
     * @param httpReq
     * @return
     */
    @DeleteMapping("/{recipeId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        recipeService.deleteRecipe(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa công thức thành công", httpReq.getRequestURI()));
    }

    /**
     * Danh sách công thức đã xóa (ADMIN: tất cả; USER: của mình).
     *
     * @param authUser
     * @param page
     * @param size
     * @param httpReq
     * @return
     */
    @GetMapping("/deleted")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> listDeleted(
            @AuthenticationPrincipal User authUser,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        PageResult<RecipeCardResponse> data = recipeService.listDeletedRecipes(authUser, userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức đã xóa thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Xóa vĩnh viễn một công thức (chỉ dành cho owner hoặc ADMIN).
     * Yêu cầu công thức phải đã được xóa mềm trước đó.
     */
    @DeleteMapping("/{recipeId}/permanent")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> hardDeleteRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        recipeService.hardDeleteRecipe(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa vĩnh viễn công thức", httpReq.getRequestURI()));
    }

    /**
     * Tạo một bản sao tùy chỉnh (fork) của một công thức đã có.
     * POST /recipes/{originalRecipeId}/fork
     */
    @PostMapping("/{originalRecipeId}/fork")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RecipeResponse>> forkRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long originalRecipeId,
            @Valid @RequestBody ForkRecipeRequest req,
            HttpServletRequest httpReq
    ) {
        RecipeResponse data = recipeService.forkRecipe(authUser.getUserId(), originalRecipeId, req);
        return ResponseEntity.ok(ApiResponse.success("Sao chép và tùy chỉnh công thức thành công.", data, httpReq.getRequestURI()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> searchRecipes(
            @RequestParam("query") String query,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        PageResult<RecipeCardResponse> data = recipeService.searchPublicRecipes(query, page, size);
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm công thức thành công", data, httpReq.getRequestURI()));
    }

    @GetMapping("/{recipeId}/images/history")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<AllRecipeImagesResponse>> getAllRecipeImages(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        AllRecipeImagesResponse data = recipeImageService.getAllRecipeImages(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử ảnh thành công", data, httpReq.getRequestURI()));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> listPopular(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        PageResult<RecipeCardResponse> data = recipeService.listPopular(page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức phổ biến thành công", data, httpReq.getRequestURI()));
    }

    @GetMapping("/easy-to-cook")
    public ResponseEntity<ApiResponse<PageResult<RecipeCardResponse>>> listEasyToCook(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "12") int size,
            HttpServletRequest httpReq
    ) {
        PageResult<RecipeCardResponse> data = recipeService.listEasyToCook(page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách công thức dễ nấu thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Thêm một bước mới vào cuối công thức (chỉ chủ sở hữu hoặc ADMIN).
     * POST /recipes/{recipeId}/steps
     */
    @PostMapping(value = "/{recipeId}/steps", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> addStep(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @RequestParam(value = "content") String content,
            @RequestParam(value = "suggestedTime") Integer suggestedTime,
            @RequestParam(value = "tips") String tips,
            @RequestPart(value = "addFiles", required = false) List<MultipartFile> addFiles,
            HttpServletRequest httpReq
    ) throws IOException {

        RecipeResponse data = stepImageService.addStep(
                authUser.getUserId(), recipeId, content, suggestedTime, tips, addFiles
        );
        return ResponseEntity.ok(ApiResponse.success("Thêm bước mới thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Sắp xếp lại thứ tự các bước của một công thức.
     * PUT /recipes/{recipeId}/steps/reorder
     */
    @PutMapping("/{recipeId}/steps/reorder")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> reorderSteps(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @Valid @RequestBody RecipeStepReorderRequest req,
            HttpServletRequest httpReq
    ) {
        RecipeResponse data = stepImageService.reorderSteps(authUser.getUserId(), recipeId, req);
        return ResponseEntity.ok(ApiResponse.success("Sắp xếp lại các bước thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Thêm một hoặc nhiều nguyên liệu vào cuối danh sách của công thức.
     * POST /recipes/{recipeId}/ingredients
     */
    @PostMapping("/{recipeId}/ingredients")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> addIngredients(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @Valid @RequestBody AddIngredientsRequest req,
            HttpServletRequest httpReq
    ) {
        RecipeResponse data = recipeService.addIngredients(authUser.getUserId(), recipeId, req);
        return ResponseEntity.ok(ApiResponse.success("Thêm nguyên liệu thành công", data, httpReq.getRequestURI()));
    }

    /**
     * Tạo link chia sẻ và mã QR cho một công thức.
     * POST /recipes/{recipeId}/share
     */
    @PostMapping("/{recipeId}/share")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShareResponse>> shareRecipe(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            HttpServletRequest httpReq
    ) {
        ShareResponse data = shareService.createShareLink(authUser.getUserId(), recipeId);
        return ResponseEntity.ok(ApiResponse.success("Tạo link chia sẻ thành công.", data, httpReq.getRequestURI()));
    }

    /**
     * Truy cập chi tiết công thức thông qua mã chia sẻ.
     * GET /recipes/shared/{shareCode}
     */
    @GetMapping("/shared/{shareCode}")
    public ResponseEntity<ApiResponse<RecipeResponse>> getRecipeByShareCode(
            @PathVariable String shareCode,
            @AuthenticationPrincipal User authUserOrNull,
            HttpServletRequest httpReq
    ) {
        Long viewerId = (authUserOrNull != null) ? authUserOrNull.getUserId() : null;

        RecipeResponse data = shareService.getRecipeByShareCode(shareCode, viewerId);

        return ResponseEntity.ok(ApiResponse.success("Lấy công thức chia sẻ thành công.", data, httpReq.getRequestURI()));
    }

    /**
     * Xóa một hoặc nhiều nguyên liệu khỏi công thức.
     * DELETE /recipes/{recipeId}/ingredients
     */
    @DeleteMapping("/{recipeId}/ingredients")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> deleteIngredients(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @Valid @RequestBody DeleteIngredientsRequest req, // Nhận ID từ body
            HttpServletRequest httpReq
    ) {
        Map<String, Integer> result = recipeService.deleteIngredients(authUser.getUserId(), recipeId, req);
        int deletedCount = result.getOrDefault("deletedCount", 0);
        String message = String.format("Đã xóa thành công %d nguyên liệu khỏi công thức.", deletedCount);

        return ResponseEntity.ok(ApiResponse.success(message, result, httpReq.getRequestURI()));
    }

    /**
     * Xóa một hoặc nhiều bước (step) khỏi công thức.
     * DELETE /recipes/{recipeId}/steps
     */
    @DeleteMapping("/{recipeId}/steps")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> deleteSteps(
            @AuthenticationPrincipal User authUser,
            @PathVariable Long recipeId,
            @Valid @RequestBody DeleteRecipeStepsRequest req,
            HttpServletRequest httpReq
    ) {
        Map<String, Integer> result = stepImageService.deleteSteps(authUser.getUserId(), recipeId, req);
        int deletedCount = result.getOrDefault("deletedCount", 0);
        String message = String.format("Đã xóa thành công %d bước. Các bước còn lại đã được sắp xếp lại.", deletedCount);

        return ResponseEntity.ok(ApiResponse.success(message, result, httpReq.getRequestURI()));
    }
}
