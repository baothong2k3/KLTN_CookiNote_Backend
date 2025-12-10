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

import fit.kltn_cookinote_backend.dtos.SuggestionHistoryItem;
import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface RecipeService {
    /**
     * Tạo công thức đầy đủ (gộp) từ JSON và các file ảnh.
     *
     * @param actorUserId   ID người dùng
     * @param recipeJson    Chuỗi JSON của RecipeCreateRequest
     * @param coverImage    File ảnh bìa (nullable)
     * @param allStepImages Map chứa các ảnh steps (key: "stepImages_1", "stepImages_2", ...)
     * @return RecipeResponse hoàn chỉnh
     * @throws IOException
     */
    RecipeResponse createRecipeFull(Long actorUserId, String recipeJson,
                                    MultipartFile coverImage,
                                    Map<String, List<MultipartFile>> allStepImages) throws IOException;

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
     * @return Danh sách toàn bộ nguyên liệu của recipe
     */
    List<RecipeIngredientItem> addIngredients(Long actorUserId, Long recipeId, AddIngredientsRequest req);

    /**
     * Xóa một hoặc nhiều nguyên liệu khỏi một công thức đã tồn tại.
     * Chỉ chủ sở hữu hoặc ADMIN mới có quyền.
     *
     * @param actorUserId ID người thực hiện
     * @param recipeId    ID công thức
     * @param req         Đối tượng chứa danh sách ID nguyên liệu cần xóa
     * @return Map chứa số lượng nguyên liệu đã xóa ("deletedCount")
     */
    Map<String, Integer> deleteIngredients(Long actorUserId, Long recipeId, DeleteIngredientsRequest req);

    /**
     * Helper tập trung để xây dựng một RecipeResponse hoàn chỉnh
     * (bao gồm favorite, rating, comments).
     *
     * @param recipe       Đối tượng Recipe đã tải
     * @param viewerUserId ID của người xem (có thể null)
     * @return RecipeResponse hoàn chỉnh
     */
    RecipeResponse buildRecipeResponse(Recipe recipe, Long viewerUserId);

    /**
     * Lấy danh sách công thức với bộ lọc linh hoạt.
     *
     * @param actor        Người đang thực hiện yêu cầu (để kiểm tra quyền)
     * @param filterUserId Lọc theo chủ sở hữu (có thể null)
     * @param privacy      Lọc theo quyền riêng tư (có thể null)
     * @param deleted      Lọc theo trạng thái xóa (có thể null)
     * @param page         Số trang
     * @param size         Kích thước trang
     */
    PageResult<RecipeCardResponse> filterRecipes(User actor, Long filterUserId, Privacy privacy, Boolean deleted, int page, int size);

    /**
     * Khôi phục công thức đã xóa mềm.
     * Chỉ chủ sở hữu hoặc ADMIN mới có quyền.
     *
     * @param actorUserId ID người thực hiện
     * @param recipeId    ID công thức
     */
    void restoreRecipe(Long actorUserId, Long recipeId);

    RecipeResponse updateNutrition(Long actorUserId, Long recipeId, UpdateNutritionRequest req);

    List<PersonalizedRecipeResponse> getPersonalizedSuggestions(Long currentUserId, PersonalizedSuggestionRequest req);

    PageResult<SuggestionHistoryItem> getPersonalizedHistory(Long userId, int page, int size);

    List<PersonalizedRecipeResponse> calculateSuggestionsWithCache(Long currentUserId, PersonalizedSuggestionRequest req);

    /**
     * Lưu công thức từ gợi ý cá nhân hóa của AI.
     * - Mặc định Privacy = PRIVATE (nếu null).
     * - Mặc định Category ID = 8 (nếu null).
     * - Không sao chép Image URL từ công thức gốc.
     * - Lưu reference tới Original Recipe.
     */
    RecipeResponse savePersonalizedRecipe(Long userId, SavePersonalizedRecipeRequest req);
}
