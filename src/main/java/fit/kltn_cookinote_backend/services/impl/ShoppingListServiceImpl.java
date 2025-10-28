/*
 * @ (#) ShoppingListServiceImpl.java    1.0    27/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import fit.kltn_cookinote_backend.entities.ShoppingList;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.RecipeIngredientRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.ShoppingListRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.GeminiApiClient;
import fit.kltn_cookinote_backend.services.ShoppingListService;
import fit.kltn_cookinote_backend.utils.ShoppingListUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fit.kltn_cookinote_backend.utils.ShoppingListUtils.*;

@Service
@RequiredArgsConstructor
public class ShoppingListServiceImpl implements ShoppingListService {

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final GeminiApiClient geminiApiClient;

    private static final int CANDIDATE_POOL_SIZE = 5;

    /**
     * Helper method to get existing shopping list items for a user and recipe,
     * mapped by normalized ingredient name.
     */
    private Map<String, ShoppingList> getExistingShoppingListMap(Long userId, Long recipeId) {
        List<ShoppingList> existing = shoppingListRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId);
        return existing.stream()
                .filter(it -> it.getIngredient() != null)
                .collect(Collectors.toMap(
                        it -> normalize(it.getIngredient()),
                        Function.identity(),
                        (a, b) -> a
                ));
    }

    @Override
    @Transactional
    public SyncShoppingListResponse createFromRecipe(Long userId, Long recipeId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // Quyền xem
        if (recipe.getPrivacy() == Privacy.PRIVATE && !Objects.equals(recipe.getUser().getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền sử dụng recipe PRIVATE này.");
        }

        // (2) Kiểm tra xem đã tồn tại trước đó hay chưa
        // Chúng ta kiểm tra trước khi gọi helper getExistingShoppingListMap
        boolean existedBefore = shoppingListRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId).stream()
                .anyMatch(item -> item.getIngredient() != null);

        Map<String, ShoppingList> existByKey = getExistingShoppingListMap(userId, recipeId);

        // Lấy toàn bộ RecipeIngredient
        List<RecipeIngredient> ingredients = ingredientRepository.findByRecipe_IdOrderByIdAsc(recipeId);

        List<ShoppingList> toCreate = new ArrayList<>();

        for (RecipeIngredient ri : ingredients) {
            String name = ri.getName();
            if (name == null || normalize(name).isEmpty()) continue;

            String qty = ri.getQuantity();
            String key = normalize(name);

            ShoppingList exist = existByKey.get(key);
            if (exist != null) {
                // Mục đã tồn tại
                if (!Objects.equals(safe(qty), safe(exist.getQuantity()))) {
                    exist.setQuantity(qty);
                }
                if (!Boolean.TRUE.equals(exist.getIsFromRecipe())) {
                    exist.setIsFromRecipe(Boolean.TRUE);
                }
                existByKey.remove(key);
            } else {
                // Thêm mới
                toCreate.add(ShoppingList.builder()
                        .user(user)
                        .recipe(recipe)
                        .ingredient(canonicalize(name))
                        .quantity(qty)
                        .checked(Boolean.FALSE)
                        .isFromRecipe(Boolean.TRUE)
                        .build());
            }
        }

        // Lưu các mục mới
        if (!toCreate.isEmpty()) {
            shoppingListRepository.saveAll(toCreate);
        }

        // Xử lý các mục còn lại (mồ côi)
        for (ShoppingList remainingItem : existByKey.values()) {
            if (Boolean.TRUE.equals(remainingItem.getIsFromRecipe())) {
                remainingItem.setIsFromRecipe(Boolean.FALSE);
            }
        }

        // Tải lại danh sách cuối cùng
        List<ShoppingList> reloadedItems = shoppingListRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId);

        // Sắp xếp theo ID giảm dần (mới nhất xếp trước)
        reloadedItems.sort(Comparator.comparing(ShoppingList::getId).reversed());

        List<ShoppingListResponse> finalItems = reloadedItems.stream()
                .map(s -> toResponse(s, recipeId)).toList();

        // (3) Trả về DTO
        return new SyncShoppingListResponse(finalItems, existedBefore);
    }

    @Override
    @Transactional
    public ShoppingListResponse upsertOneStandalone(Long userId, String ingredient, String quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        String name = canonicalize(ingredient);
        if (name.isEmpty()) throw new IllegalArgumentException("Tên nguyên liệu trống.");

        // Upsert theo (user, recipe IS NULL, ingredient ignoreCase)
        ShoppingList item = shoppingListRepository
                .findByUser_UserIdAndRecipeIsNullAndIngredientIgnoreCase(userId, name)
                .map(exist -> {
                    // GIỮ checked cũ, chỉ cập nhật quantity nếu khác
                    if (!Objects.equals(safe(quantity), safe(exist.getQuantity()))) {
                        exist.setQuantity(quantity);
                    }
                    return exist;
                })
                .orElseGet(() -> shoppingListRepository.save(
                        ShoppingList.builder()
                                .user(user)
                                .recipe(null)                  // lẻ loi
                                .ingredient(name)              // canonicalized
                                .quantity(quantity)
                                .checked(Boolean.FALSE)        // mặc định chưa tick
                                .isFromRecipe(Boolean.FALSE)
                                .build()
                ));

        return toResponse(item, null);
    }

    // ===== (2) Có recipe_id: áp dụng merge giữ checked =====
    @Override
    @Transactional
    public ShoppingListResponse upsertOneInRecipe(Long userId, Long recipeId, String ingredient, String quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // Quyền xem/merge với recipe PRIVATE
        if (recipe.getPrivacy() == Privacy.PRIVATE && !Objects.equals(recipe.getUser().getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền thêm nguyên liệu vào recipe PRIVATE này.");
        }

        String name = canonicalize(ingredient);
        String key = normalize(name); // Key để kiểm tra sự tồn tại trong công thức
        if (name.isEmpty()) throw new IllegalArgumentException("Tên nguyên liệu trống.");

        // === LOGIC MỚI: KIỂM TRA NGUỒN GỐC ===
        // Lấy các key nguyên liệu GỐC của recipe
        List<RecipeIngredient> ingredients = ingredientRepository.findByRecipe_IdOrderByIdAsc(recipeId);
        Set<String> recipeKeys = ingredients.stream()
                .map(ri -> normalize(ri.getName())) // Dùng normalize để so sánh
                .collect(Collectors.toSet());

        // Mục này có trong công thức gốc (true) hay do người dùng tự thêm (false)
        final boolean isFromRecipeFlag = recipeKeys.contains(key);
        // ===================================

        // Upsert theo (user, recipe_id, ingredient ignoreCase)
        ShoppingList item = shoppingListRepository
                .findByUser_UserIdAndRecipe_IdAndIngredientIgnoreCase(userId, recipeId, name)
                .map(exist -> {
                    // GIỮ checked cũ; chỉ cập nhật quantity nếu khác
                    if (!Objects.equals(safe(quantity), safe(exist.getQuantity()))) {
                        exist.setQuantity(quantity);
                    }
                    // Cập nhật lại cờ (phòng trường hợp nó được thêm tay trước khi đồng bộ)
                    exist.setIsFromRecipe(isFromRecipeFlag);
                    return exist;
                })
                .orElseGet(() -> shoppingListRepository.save(
                        ShoppingList.builder()
                                .user(user)
                                .recipe(recipe)
                                .ingredient(name)              // canonicalized
                                .quantity(quantity)
                                .checked(Boolean.FALSE)
                                .isFromRecipe(isFromRecipeFlag) // Đặt cờ theo nguồn gốc
                                .build()
                ));

        return toResponse(item, recipeId);
    }

    @Override
    @Transactional
    public ShoppingListResponse updateItemContent(Long userId, Long itemId,
                                                  String newIngredientOrNull,
                                                  String newQuantityOrNull,
                                                  Boolean newCheckedOrNull) {
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        // Lấy item thuộc về user
        ShoppingList current = shoppingListRepository.findByIdAndUser_UserId(itemId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Item không tồn tại hoặc không thuộc về bạn: " + itemId));

        // Xác định list (recipe_id có thể null)
        Long recipeId = current.getRecipe() != null ? current.getRecipe().getId() : null;

        // Xác định xem có thay đổi nội dung (tên/số lượng) không
        boolean contentChanged = newIngredientOrNull != null || newQuantityOrNull != null;

        // Chuẩn hoá & re-validate dữ liệu mới (nếu có)
        String nameNew = (newIngredientOrNull != null)
                ? normalizeAndValidateName(newIngredientOrNull)
                : current.getIngredient();

        String qtyNew = (newQuantityOrNull != null)
                ? normalizeAndValidateQuantity(newQuantityOrNull)
                : null; // null = không đổi

        // Nếu đổi tên, kiểm tra trùng trong cùng list để MERGE
        boolean nameChanged = !Objects.equals(nameNew, current.getIngredient());

        if (nameChanged) {
            Optional<ShoppingList> dup = (recipeId == null)
                    ? shoppingListRepository.findByUser_UserIdAndRecipeIsNullAndIngredientIgnoreCase(userId, nameNew)
                    : shoppingListRepository.findByUser_UserIdAndRecipe_IdAndIngredientIgnoreCase(userId, recipeId, nameNew);

            if (dup.isPresent() && !dup.get().getId().equals(current.getId())) {
                // Merge vào item trùng
                ShoppingList target = dup.get();

                // checked = OR(current, target, newChecked nếu có)
                boolean mergedChecked = Boolean.TRUE.equals(target.getChecked())
                        || Boolean.TRUE.equals(current.getChecked())
                        || Boolean.TRUE.equals(newCheckedOrNull);

                // quantity: ưu tiên qtyNew nếu gửi; nếu không, giữ target.quantity
                String mergedQty = (qtyNew != null) ? qtyNew : target.getQuantity();

                target.setChecked(mergedChecked);
                target.setQuantity(mergedQty);
                target.setIngredient(nameNew); // canonicalized

                // Khi merge do đổi tên thủ công, kết quả luôn là 'false'
                target.setIsFromRecipe(Boolean.FALSE);


                // Xoá item hiện tại để tránh duplicate
                shoppingListRepository.delete(current);

                return toResponse(target, recipeId);
            }
        }

        // Không cần merge -> cập nhật trực tiếp current
        current.setIngredient(nameNew); // canonicalized
        if (qtyNew != null) current.setQuantity(qtyNew);
        if (newCheckedOrNull != null) current.setChecked(newCheckedOrNull);

        // Nếu tên hoặc số lượng bị thay đổi thủ công qua API này,
        // thì đánh dấu là do người dùng tự chỉnh sửa (false)
        if (contentChanged) {
            current.setIsFromRecipe(Boolean.FALSE);
        }
        // Nếu chỉ thay đổi 'checked', không đổi cờ isFromRecipe

        return toResponse(current, recipeId);
    }

    @Override
    @Transactional
    public ShoppingListResponse moveItem(Long userId, Long itemId, Long targetRecipeIdOrNull) {
        // ensure user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        // item hiện tại (thuộc về user)
        ShoppingList current = shoppingListRepository.findByIdAndUser_UserId(itemId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Item không tồn tại hoặc không thuộc về bạn: " + itemId));

        Long sourceRecipeId = current.getRecipe() != null ? current.getRecipe().getId() : null;

        // nếu không đổi list -> no-op
        if (Objects.equals(sourceRecipeId, targetRecipeIdOrNull)) {
            return toResponse(current, sourceRecipeId);
        }

        Recipe targetRecipe = validateAndGetTargetRecipe(userId, targetRecipeIdOrNull);

        String name = current.getIngredient(); // Tên đã canonicalized
        String key = normalize(name);         // Key để kiểm tra

        // Xác định cờ isFromRecipe cho đích
        boolean targetIsFromRecipe = (targetRecipe != null) && checkIfIngredientInRecipe(targetRecipe.getId(), key);

        // Tìm trùng trong list đích
        Optional<ShoppingList> dupOpt = findDuplicateInTargetList(userId, targetRecipeIdOrNull, name);

        if (dupOpt.isPresent() && !dupOpt.get().getId().equals(current.getId())) {
            // Có trùng -> Merge
            return handleMoveMerge(current, dupOpt.get(), targetIsFromRecipe, targetRecipeIdOrNull);
        } else {
            // Không trùng -> Chỉ di chuyển
            current.setRecipe(targetRecipe);
            current.setIsFromRecipe(targetIsFromRecipe);
            // shoppingListRepository.save(current); // Không cần thiết nếu @Transactional quản lý
            return toResponse(current, targetRecipeIdOrNull);
        }
    }

    /**
     * Helper method: Xử lý logic merge khi di chuyển item và có item trùng ở đích.
     */
    private ShoppingListResponse handleMoveMerge(ShoppingList sourceItem, ShoppingList targetItem, boolean targetIsFromRecipe, Long targetRecipeId) {
        // Merge checked status (OR logic)
        boolean mergedChecked = Boolean.TRUE.equals(targetItem.getChecked()) || Boolean.TRUE.equals(sourceItem.getChecked());
        targetItem.setChecked(mergedChecked);

        // Merge quantity (ưu tiên target nếu có, không thì lấy source) - Có thể điều chỉnh logic này
        String mergedQty = (targetItem.getQuantity() != null) ? targetItem.getQuantity() : sourceItem.getQuantity();
        targetItem.setQuantity(mergedQty);

        // Cập nhật cờ isFromRecipe dựa trên đích đến
        targetItem.setIsFromRecipe(targetIsFromRecipe);

        // shoppingListRepository.save(targetItem); // Không cần thiết nếu @Transactional quản lý

        // Xóa item gốc sau khi merge
        shoppingListRepository.delete(sourceItem);

        return toResponse(targetItem, targetRecipeId);
    }

    /**
     * Helper method: Tìm item trùng tên (ignore case) trong danh sách đích.
     */
    private Optional<ShoppingList> findDuplicateInTargetList(Long userId, Long targetRecipeId, String ingredientName) {
        if (targetRecipeId == null) {
            return shoppingListRepository.findByUser_UserIdAndRecipeIsNullAndIngredientIgnoreCase(userId, ingredientName);
        } else {
            return shoppingListRepository.findByUser_UserIdAndRecipe_IdAndIngredientIgnoreCase(userId, targetRecipeId, ingredientName);
        }
    }

    /**
     * Helper method: Kiểm tra và lấy Recipe đích (nếu có).
     */
    private Recipe validateAndGetTargetRecipe(Long userId, Long targetRecipeId) {
        if (targetRecipeId == null) {
            return null; // Di chuyển đến danh sách standalone
        }
        Recipe targetRecipe = recipeRepository.findById(targetRecipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe đích không tồn tại: " + targetRecipeId));

        // Kiểm tra quyền nếu recipe đích là PRIVATE
        if (targetRecipe.getPrivacy() == Privacy.PRIVATE && !Objects.equals(targetRecipe.getUser().getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền chuyển vào recipe PRIVATE này.");
        }
        return targetRecipe;
    }

    /**
     * Helper method: Kiểm tra xem một nguyên liệu (theo key đã chuẩn hóa) có tồn tại trong công thức không.
     */
    private boolean checkIfIngredientInRecipe(Long recipeId, String normalizedKey) {
        if (recipeId == null || normalizedKey == null || normalizedKey.isEmpty()) {
            return false;
        }
        List<RecipeIngredient> ingredients = ingredientRepository.findByRecipe_IdOrderByIdAsc(recipeId);
        return ingredients.stream()
                .map(RecipeIngredient::getName)
                .filter(Objects::nonNull)
                .map(ShoppingListUtils::normalize)
                .anyMatch(normalizedKey::equals);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupedShoppingListResponse> getAllGroupedByRecipe(Long userId) {
        List<ShoppingList> allItems = shoppingListRepository.findByUser_UserIdOrderByIdDesc(userId);

        Map<String, List<ShoppingList>> grouped = new LinkedHashMap<>();

        // Gom nhóm thông minh hơn
        for (ShoppingList item : allItems) {
            String key;
            if (item.getRecipe() != null) {
                key = "recipe-" + item.getRecipe().getId();
            } else if (item.getOriginalRecipeTitle() != null) {
                // Nhóm các item của cùng một recipe đã bị xóa dựa trên tên gốc
                key = "deleted-" + item.getOriginalRecipeTitle();
            } else {
                key = "standalone"; // Các item không thuộc recipe nào
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        List<GroupedShoppingListResponse> result = new ArrayList<>();
        grouped.forEach((key, items) -> {
            ShoppingList firstItem = items.get(0);
            Recipe recipe = firstItem.getRecipe();

            String title;
            String imageUrl = null;
            Long recipeId = null;

            if (recipe != null) {
                recipeId = recipe.getId();
                title = Boolean.TRUE.equals(firstItem.getIsRecipeDeleted())
                        ? "[ĐÃ XÓA] " + recipe.getTitle()
                        : recipe.getTitle();
                imageUrl = recipe.getImageUrl();
            } else if (firstItem.getOriginalRecipeTitle() != null) {
                title = "[ĐÃ XÓA] " + firstItem.getOriginalRecipeTitle();
            } else {
                title = "Khác";
            }

            List<GroupedShoppingListResponse.ShoppingListItem> itemDtos = items.stream()
                    .map(item -> GroupedShoppingListResponse.ShoppingListItem.builder()
                            .id(item.getId())
                            .ingredient(item.getIngredient())
                            .quantity(item.getQuantity())
                            .checked(item.getChecked())
                            .isFromRecipe(item.getIsFromRecipe())
                            .build())
                    .collect(Collectors.toList());

            result.add(GroupedShoppingListResponse.builder()
                    .recipeId(recipeId)
                    .recipeTitle(title)
                    .recipeImageUrl(imageUrl)
                    .items(itemDtos)
                    .build());
        });

        return result;
    }

    @Override
    @Transactional(readOnly = true) // Thêm (readOnly = true) để tối ưu
    public PageResult<RecipeSuggestionResponse> suggestRecipes(Long userId, List<String> ingredientNames, Pageable pageable) {

        // ----- BƯỚC 1: LẤY ỨNG VIÊN TỪ DATABASE -----
        // Tạo yêu cầu phân trang chỉ lấy 5 ứng viên hàng đầu (trang 0, 5 phần tử)
        Pageable candidatesPageable = PageRequest.of(0, CANDIDATE_POOL_SIZE);

        // Truy vấn DB: Tìm 5 công thức PUBLIC có nhiều nguyên liệu khớp nhất
        Page<Recipe> candidates = recipeRepository.findCandidateRecipesByIngredients(ingredientNames, candidatesPageable);

        if (candidates.isEmpty()) {
            // Nếu không có ứng viên nào, trả về trang rỗng
            return new PageResult<>(pageable.getPageNumber(), pageable.getPageSize(), 0, 0, false, Collections.emptyList());
        }

        // ----- BƯỚC 1.5: TẢI TRƯỚC DỮ LIỆU (EAGER LOADING) -----
        // Buộc Hibernate tải collection 'ingredients' ngay lập tức
        List<Recipe> candidateList = candidates.getContent();
        candidateList.forEach(recipe -> Hibernate.initialize(recipe.getIngredients()));

        // ----- BƯỚC 2: GỌI AI CHẤM ĐIỂM HÀNG LOẠT (BATCH CALL) -----
        // Gửi toàn bộ 5 ứng viên trong MỘT cuộc gọi API duy nhất
        List<AiScoreResponse> scores = geminiApiClient.getSuggestionScoresBatch(ingredientNames, candidateList);

        // ----- BƯỚC 3: ÁNH XẠ KẾT QUẢ -----
        // Chuyển danh sách điểm số (List) thành Map để tra cứu nhanh bằng ID công thức
        Map<Long, AiScoreResponse> scoreMap = scores.stream()
                .collect(Collectors.toMap(AiScoreResponse::getId, Function.identity(), (a, b) -> a));

        // Kết hợp công thức (Recipe) với điểm số (Score) của nó
        // Sửa Warning: .toList()
        List<RecipeSuggestionResponse> scoredSuggestions = candidateList.stream()
                .map(recipe -> {
                    // Lấy điểm từ map; nếu AI lỗi (không trả về ID), dùng fallback
                    AiScoreResponse score = scoreMap.getOrDefault(recipe.getId(), geminiApiClient.createFallbackScore(recipe.getId()));
                    RecipeCardResponse card = RecipeCardResponse.from(recipe);

                    return new RecipeSuggestionResponse(
                            card,
                            score.getMainIngredientMatchScore(),
                            score.getOverallMatchScore(),
                            score.getJustification()
                    );
                })
                .toList();

        // ----- BƯỚC 4: SẮP XẾP (IN-MEMORY) -----
        // Sắp xếp danh sách 5 kết quả dựa trên điểm số (ưu tiên mainIngredientMatchScore)
        List<RecipeSuggestionResponse> sortedList = new ArrayList<>(scoredSuggestions);
        sortedList.sort(
                Comparator.comparing(RecipeSuggestionResponse::mainIngredientMatchScore)
                        .thenComparing(RecipeSuggestionResponse::overallMatchScore)
                        .reversed() // Sắp xếp điểm cao nhất lên đầu
        );

        // ----- BƯỚC 5: PHÂN TRANG (IN-MEMORY) -----
        // Áp dụng logic phân trang (page, size) cho danh sách 5 kết quả đã sắp xếp
        int pageSize = pageable.getPageSize();
        int currentPage = pageable.getPageNumber();
        int startItem = currentPage * pageSize;

        List<RecipeSuggestionResponse> paginatedList;
        int totalItems = sortedList.size(); // Tổng số item (tối đa 5)

        if (startItem < totalItems) {
            int toIndex = Math.min(startItem + pageSize, totalItems);
            paginatedList = sortedList.subList(startItem, toIndex);
        } else {
            paginatedList = Collections.emptyList();
        }

        int totalPages = (int) Math.ceil((double) totalItems / (double) pageSize);
        boolean hasNext = (currentPage + 1) < totalPages;

        // ----- BƯỚC 6: TRẢ VỀ KẾT QUẢ -----
        return new PageResult<>(
                currentPage,
                pageSize,
                totalItems,
                totalPages,
                hasNext,
                paginatedList
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ShoppingListSyncCheckResponse checkRecipeUpdates(Long userId, Long recipeId) {
        // 1. Tải dữ liệu cần thiết
        userRepository.findById(userId) // Tải user để kiểm tra tồn tại (không cần thiết nếu đã xác thực)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // Quyền xem
        if (recipe.getPrivacy() == Privacy.PRIVATE && !Objects.equals(recipe.getUser().getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem recipe PRIVATE này.");
        }

        // Danh sách mua sắm hiện tại của user cho recipe này
        List<ShoppingList> currentShoppingList = shoppingListRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId);
        // Map theo key chuẩn hóa để tra cứu nhanh
        Map<String, ShoppingList> shoppingMap = currentShoppingList.stream()
                .filter(it -> it.getIngredient() != null)
                .collect(Collectors.toMap(
                        it -> normalize(it.getIngredient()),
                        Function.identity(),
                        (a, b) -> a // Giữ lại cái đầu tiên nếu có trùng key (hiếm khi xảy ra)
                ));

        // Nguyên liệu mới nhất từ công thức
        List<RecipeIngredient> recipeIngredients = ingredientRepository.findByRecipe_IdOrderByIdAsc(recipeId);

        // 2. Thực hiện so sánh
        List<ShoppingListSyncCheckResponse.SyncItem> addedItems = new ArrayList<>();
        List<ShoppingListSyncCheckResponse.SyncItem> removedItems = new ArrayList<>();
        List<ShoppingListSyncCheckResponse.UpdatedSyncItem> updatedItems = new ArrayList<>();
        List<ShoppingListSyncCheckResponse.SyncItem> manualItemsNotInRecipe = new ArrayList<>();

        // 2a. Duyệt qua nguyên liệu công thức -> Tìm mục mới (added) và mục cập nhật (updated)
        for (RecipeIngredient ri : recipeIngredients) {
            String key = normalize(ri.getName());
            ShoppingList existingShoppingItem = shoppingMap.get(key);

            if (existingShoppingItem == null) {
                // Không có trong shopping list -> Thêm mới
                addedItems.add(ShoppingListSyncCheckResponse.SyncItem.builder()
                        .ingredient(canonicalize(ri.getName()))
                        .quantity(ri.getQuantity())
                        .build());
            } else {
                // Có trong shopping list -> Kiểm tra quantity
                if (!Objects.equals(safe(ri.getQuantity()), safe(existingShoppingItem.getQuantity()))) {
                    updatedItems.add(ShoppingListSyncCheckResponse.UpdatedSyncItem.builder()
                            .shoppingListId(existingShoppingItem.getId())
                            .ingredient(canonicalize(ri.getName()))
                            .oldQuantity(existingShoppingItem.getQuantity())
                            .newQuantity(ri.getQuantity())
                            .build());
                }
                // Đánh dấu đã xử lý
                shoppingMap.remove(key);
            }
        }

        // 2b. Duyệt qua các mục còn lại trong shoppingMap -> Tìm mục bị xóa (removed) và mục tự thêm (manual)
        for (ShoppingList remainingShoppingItem : shoppingMap.values()) {
            if (Boolean.TRUE.equals(remainingShoppingItem.getIsFromRecipe())) {
                // Mục này từ công thức nhưng không có trong recipeMap nữa -> Bị xóa (removed)
                removedItems.add(ShoppingListSyncCheckResponse.SyncItem.builder()
                        .shoppingListId(remainingShoppingItem.getId())
                        .ingredient(remainingShoppingItem.getIngredient()) // Đã chuẩn hóa
                        .quantity(remainingShoppingItem.getQuantity())
                        .build());
            } else {
                // Mục này do người dùng tự thêm (isFromRecipe=false) và không có trong recipeMap
                manualItemsNotInRecipe.add(ShoppingListSyncCheckResponse.SyncItem.builder()
                        .shoppingListId(remainingShoppingItem.getId())
                        .ingredient(remainingShoppingItem.getIngredient())
                        .quantity(remainingShoppingItem.getQuantity())
                        .build());
            }
        }

        // 3. Xây dựng và trả về kết quả
        return ShoppingListSyncCheckResponse.builder()
                .addedItems(addedItems)
                .removedItems(removedItems)
                .updatedItems(updatedItems)
                .manualItemsNotInRecipe(manualItemsNotInRecipe)
                .build();
    }

    @Override
    @Transactional
    public ShoppingListResponse checkItem(Long userId, Long itemId) {
        // Tải item và đảm bảo nó thuộc về user
        ShoppingList item = shoppingListRepository.findByIdAndUser_UserId(itemId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Mục không tồn tại hoặc không thuộc về bạn: " + itemId));

        // Đánh dấu là true
        item.setChecked(Boolean.TRUE);

        shoppingListRepository.save(item);

        // Trả về response
        Long recipeId = item.getRecipe() != null ? item.getRecipe().getId() : null;
        return toResponse(item, recipeId);
    }

    @Override
    @Transactional
    public Map<String, Integer> deleteItems(Long userId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách ID cần xóa không được rỗng.");
        }

        // Kiểm tra user tồn tại (nếu cần)
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        // Tải tất cả items dựa trên danh sách ID được yêu cầu
        List<ShoppingList> foundItems = shoppingListRepository.findAllById(itemIds);
        Map<Long, ShoppingList> foundItemsMap = foundItems.stream()
                .collect(Collectors.toMap(ShoppingList::getId, item -> item));

        // --- KIỂM TRA TÍNH HỢP LỆ CỦA itemIds ---
        List<Long> invalidIds = new ArrayList<>(); // Lưu các ID không tìm thấy
        List<Long> unauthorizedIds = new ArrayList<>(); // Lưu các ID không thuộc về user

        for (Long requestedId : itemIds) {
            ShoppingList item = foundItemsMap.get(requestedId);
            if (item == null) {
                invalidIds.add(requestedId); // ID không tồn tại trong DB
            } else if (!item.getUser().getUserId().equals(userId)) {
                unauthorizedIds.add(requestedId); // ID tồn tại nhưng không thuộc về user này
            }
        }

        // Nếu có bất kỳ ID không hợp lệ hoặc không được phép, ném Exception
        if (!invalidIds.isEmpty() || !unauthorizedIds.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("Không thể xóa các mục sau: ");
            if (!invalidIds.isEmpty()) {
                errorMessage.append("Không tìm thấy ID: ").append(invalidIds).append(". ");
            }
            if (!unauthorizedIds.isEmpty()) {
                errorMessage.append("Không có quyền xóa ID: ").append(unauthorizedIds).append(".");
            }
            throw new IllegalArgumentException(errorMessage.toString().trim());
        }
        // --- KẾT THÚC KIỂM TRA ---

        // Nếu tất cả ID đều hợp lệ và thuộc về user, tiến hành xóa
        // Lúc này, list `foundItems` chính là danh sách các items cần xóa
        int deletedCount = foundItems.size();

        if (deletedCount > 0) {
            shoppingListRepository.deleteAll(foundItems); // Xóa các mục hợp lệ đã tìm thấy
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        return result;
    }
}