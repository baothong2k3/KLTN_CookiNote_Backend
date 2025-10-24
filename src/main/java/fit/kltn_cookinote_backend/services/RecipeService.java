/*
 * @ (#) RecipeService.java    1.0    11/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.AddIngredientsRequest;
import fit.kltn_cookinote_backend.dtos.request.ForkRecipeRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeCreateRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.User;

import java.util.List;

public interface RecipeService {
    RecipeResponse createByRecipe(Long id, RecipeCreateRequest req);

    RecipeResponse getDetail(Long viewerUserIdOrNull, Long recipeId);

    PageResult<RecipeCardResponse> listPublicByCategory(Long categoryId, int page, int size);

    PageResult<RecipeCardResponse> listPublic(int page, int size);

    PageResult<RecipeCardResponse> listPopular(int page, int size);

    PageResult<RecipeCardResponse> listByOwner(Long ownerUserId, Long viewerUserIdOrNull, int page, int size);

    List<RecipeStepItem> getSteps(Long viewerUserIdOrNull, Long recipeId);

    List<RecipeIngredientItem> getIngredients(Long viewerUserIdOrNull, Long recipeId);

    RecipeResponse updateContent(Long actorUserId, Long recipeId, RecipeUpdateRequest req);

    void deleteRecipe(Long actorUserId, Long recipeId);

    PageResult<RecipeCardResponse> listDeletedRecipes(User actor, Long filterUserId, int page, int size);

    /**
     * Xóa vĩnh viễn một công thức đã bị soft-delete.
     *
     * @param actorUserId ID của người thực hiện.
     * @param recipeId    ID của công thức cần xóa.
     */
    void hardDeleteRecipe(Long actorUserId, Long recipeId);

    /**
     * Tạo một bản sao (fork) của công thức đã có để người dùng hiện tại tùy chỉnh và sở hữu.
     *
     * @param clonerUserId     ID của người dùng thực hiện sao chép.
     * @param originalRecipeId ID của công thức gốc.
     * @param req              Dữ liệu tùy chỉnh cho công thức mới.
     * @return Công thức mới đã được tạo.
     */
    RecipeResponse forkRecipe(Long clonerUserId, Long originalRecipeId, ForkRecipeRequest req);

    PageResult<RecipeCardResponse> searchPublicRecipes(String query, int page, int size);

    PageResult<RecipeCardResponse> listEasyToCook(int page, int size);

    /**
     * Thêm một hoặc nhiều nguyên liệu vào cuối danh sách của một công thức đã tồn tại.
     * Chỉ chủ sở hữu hoặc ADMIN mới có quyền.
     * Các nguyên liệu trùng tên (sau khi chuẩn hóa) sẽ bị bỏ qua.
     *
     * @param actorUserId ID người thực hiện
     * @param recipeId    ID công thức
     * @param req         Đối tượng chứa danh sách nguyên liệu cần thêm
     * @return RecipeResponse đã được cập nhật
     */
    RecipeResponse addIngredients(Long actorUserId, Long recipeId, AddIngredientsRequest req);

}
