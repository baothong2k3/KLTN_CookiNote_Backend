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
import fit.kltn_cookinote_backend.enums.IngredientType;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.RecipeIngredientRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.ShoppingListRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.IngredientCategoryService;
import fit.kltn_cookinote_backend.services.IngredientClassificationService;
import fit.kltn_cookinote_backend.services.IngredientSynonymService;
import fit.kltn_cookinote_backend.services.ShoppingListService;
import fit.kltn_cookinote_backend.utils.ShoppingListUtils;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final IngredientClassificationService ingredientClassificationService;
    private final IngredientSynonymService synonymService;

    private final IngredientCategoryService ingredientCategoryService;

    private static final int CANDIDATE_POOL_SIZE = 10;

    @Override
    @Transactional(readOnly = true)
    public List<GroupedShoppingListResponse> getAllShoppingLists(Long userId, String groupBy) {
        List<ShoppingList> allItems = shoppingListRepository.findByUser_UserIdOrderByIdDesc(userId);

        // 1. GOM NHÓM THEO DANH MỤC (CATEGORY)
        if ("category".equalsIgnoreCase(groupBy)) {
            return groupByCategory(allItems);
        }

        // 2. GOM NHÓM THEO CÔNG THỨC (RECIPE - Mặc định)
        return groupByRecipe(allItems);
    }

    // --- Logic gom nhóm theo Recipe (Refactor từ code cũ của bạn) ---
    private List<GroupedShoppingListResponse> groupByRecipe(List<ShoppingList> allItems) {
        Map<String, List<ShoppingList>> grouped = new LinkedHashMap<>();

        // Logic gom nhóm giữ nguyên như cũ
        for (ShoppingList item : allItems) {
            String key;
            if (item.getRecipe() != null) {
                key = "recipe-" + item.getRecipe().getId();
            } else if (item.getOriginalRecipeTitle() != null) {
                key = "deleted-" + item.getOriginalRecipeTitle();
            } else {
                key = "standalone";
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        List<GroupedShoppingListResponse> result = new ArrayList<>();
        grouped.forEach((key, items) -> {
            ShoppingList firstItem = items.get(0);
            Recipe recipe = firstItem.getRecipe();

            Long recipeId = null;
            String title;
            String imageUrl = null;
            Boolean isDeleted = false;

            if (recipe != null) {
                recipeId = recipe.getId();
                title = Boolean.TRUE.equals(firstItem.getIsRecipeDeleted())
                        ? "[ĐÃ XÓA] " + recipe.getTitle()
                        : recipe.getTitle();
                imageUrl = recipe.getImageUrl();
                isDeleted = Boolean.TRUE.equals(firstItem.getIsRecipeDeleted());
            } else if (firstItem.getOriginalRecipeTitle() != null) {
                title = "[ĐÃ XÓA] " + firstItem.getOriginalRecipeTitle();
                isDeleted = true;
            } else {
                title = "Khác";
            }

            result.add(GroupedShoppingListResponse.builder()
                    .recipeId(recipeId)
                    .recipeTitle(title)
                    .recipeImageUrl(imageUrl)
                    .isRecipeDeleted(isDeleted)
                    .type("RECIPE") // <--- Đánh dấu loại
                    .items(mapToItems(items))
                    .build());
        });
        return result;
    }

    // --- Logic gom nhóm theo Category (Mới) ---
    private List<GroupedShoppingListResponse> groupByCategory(List<ShoppingList> allItems) {
        // Gom nhóm
        Map<String, List<ShoppingList>> grouped = allItems.stream()
                .collect(Collectors.groupingBy(item ->
                        ingredientCategoryService.getCategory(item.getIngredient())
                ));

        List<GroupedShoppingListResponse> result = new ArrayList<>();

        // Định nghĩa thứ tự hiển thị ưu tiên khi đi siêu thị
        List<String> priority = List.of(
                "Rau củ & Trái cây",       // Khớp với JSON
                "Thịt & Gia cầm",
                "Hải sản",                 // Khớp với JSON
                "Trứng - Sữa & Chế phẩm",  // Cập nhật tên cho chuẩn
                "Gạo - Bột & Ngũ cốc",     // Khớp với JSON
                "Gia vị & Đồ khô",         // Khớp với JSON
                "Đồ uống",
                "Khác"                     // Luôn để Khác cuối cùng trong list này
        );

        // Duyệt theo thứ tự ưu tiên
        for (String catName : priority) {
            if (grouped.containsKey(catName)) {
                result.add(buildCategoryResponse(catName, grouped.remove(catName)));
            }
        }

        // Các nhóm còn lại (nếu có)
        grouped.forEach((catName, items) -> result.add(buildCategoryResponse(catName, items)));

        return result;
    }

    // --- Hàm mới: Map và gộp các item trùng tên (Dùng cho Category) ---
    private List<GroupedShoppingListResponse.ShoppingListItem> mapAndMergeItems(List<ShoppingList> items) {
        // 1. Gom nhóm theo tên nguyên liệu (đã chuẩn hóa) để tìm các mục trùng
        Map<String, List<ShoppingList>> groupedByName = items.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getIngredient().trim().toLowerCase(), // Key chuẩn hóa chữ thường
                        LinkedHashMap::new, // Giữ thứ tự hiển thị
                        Collectors.toList()
                ));

        List<GroupedShoppingListResponse.ShoppingListItem> result = new ArrayList<>();

        // 2. Duyệt qua từng nhóm nguyên liệu trùng tên
        groupedByName.forEach((key, duplicates) -> {
            // Lấy item đầu tiên làm đại diện (để lấy ID, tên gốc...)
            ShoppingList first = duplicates.get(0);

            if (duplicates.size() == 1) {
                // Nếu không trùng, map như bình thường
                result.add(GroupedShoppingListResponse.ShoppingListItem.builder()
                        .id(first.getId())
                        .ingredient(first.getIngredient())
                        .quantity(first.getQuantity())
                        .checked(first.getChecked())
                        .isFromRecipe(first.getIsFromRecipe())
                        .build());
            } else {
                // Nếu có trùng -> GỘP

                // Gộp quantity bằng dấu " + "
                String mergedQuantity = duplicates.stream()
                        .map(ShoppingList::getQuantity)
                        .filter(q -> q != null && !q.trim().isEmpty())
                        .collect(Collectors.joining(" + "));

                // Logic gộp trạng thái Checked:
                // (Ví dụ: Chỉ checked khi TẤT CẢ đều checked, hoặc dùng trạng thái của cái đầu tiên)
                // Ở đây mình dùng logic: Nếu item đại diện chưa check -> hiển thị chưa check.
                Boolean isChecked = first.getChecked();

                // Logic gộp isFromRecipe: Nếu có bất kỳ cái nào từ recipe -> true
                Boolean isFromRecipe = duplicates.stream().anyMatch(sl -> Boolean.TRUE.equals(sl.getIsFromRecipe()));

                result.add(GroupedShoppingListResponse.ShoppingListItem.builder()
                        .id(first.getId()) // Lưu ý: Chỉ thao tác (xóa/check) được trên item đầu tiên
                        .ingredient(first.getIngredient())
                        .quantity(mergedQuantity.isEmpty() ? null : mergedQuantity)
                        .checked(isChecked)
                        .isFromRecipe(isFromRecipe)
                        .build());
            }
        });

        return result;
    }

    private GroupedShoppingListResponse buildCategoryResponse(String catName, List<ShoppingList> items) {
        // Sắp xếp: Chưa mua lên đầu
        items.sort(Comparator.comparing(ShoppingList::getChecked).thenComparing(ShoppingList::getId));

        return GroupedShoppingListResponse.builder()
                .recipeId(null)               // Category không có ID Long, để null
                .recipeTitle(catName)         // Tái sử dụng field title
                .recipeImageUrl(getIcon(catName)) // Tái sử dụng field image làm icon
                .isRecipeDeleted(false)
                .type("CATEGORY")             // <--- Đánh dấu loại
                .items(mapAndMergeItems(items))
                .build();
    }

    private List<GroupedShoppingListResponse.ShoppingListItem> mapToItems(List<ShoppingList> items) {
        return items.stream()
                .map(item -> GroupedShoppingListResponse.ShoppingListItem.builder()
                        .id(item.getId())
                        .ingredient(item.getIngredient())
                        .quantity(item.getQuantity())
                        .checked(item.getChecked())
                        .isFromRecipe(item.getIsFromRecipe())
                        .build())
                .collect(Collectors.toList());
    }

    // --- Cập nhật hàm lấy Icon cho khớp tên ---
    private String getIcon(String catName) {
        if (catName == null) return "https://img.icons8.com/color/96/shopping-basket-2.png";

        // Dùng contains hoặc switch case chính xác
        return switch (catName) {
            case "Thịt & Gia cầm" -> "https://img.icons8.com/?size=100&id=1vzbQymCwtpJ&format=png&color=000000";
            case "Hải sản" -> "https://img.icons8.com/?size=100&id=5bywjoHvTs4U&format=png&color=000000";

            // Sửa case này cho khớp JSON: "Rau củ & Trái cây"
            case "Rau củ & Trái cây", "Rau củ quả" ->
                    "https://img.icons8.com/?size=100&id=cpa3RyNsYJkU&format=png&color=000000";

            // Sửa case này cho khớp
            case "Trứng - Sữa & Chế phẩm", "Trứng & Sữa" ->
                    "https://img.icons8.com/?size=100&id=12874&format=png&color=000000";

            // Sửa case này cho khớp JSON: "Gia vị & Đồ khô"
            case "Gia vị & Đồ khô", "Đồ khô & Gia vị" ->
                    "https://img.icons8.com/?size=100&id=12898&format=png&color=000000";

            // Thêm case mới xuất hiện trong JSON của bạn
            case "Gạo - Bột & Ngũ cốc" -> "https://img.icons8.com/?size=100&id=24467&format=png&color=000000";

            case "Đồ uống" -> "https://img.icons8.com/?size=100&id=lyMuG44EXuxH&format=png&color=000000";

            default -> "https://img.icons8.com/?size=100&id=107457&format=png&color=000000"; // Icon cho nhóm "Khác"
        };
    }

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

    /**
     * Gợi ý công thức dựa trên chấm điểm nội bộ.
     * Đã nâng cấp để sử dụng TỪ ĐIỂN ĐỒNG NGHĨA.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeSuggestionResponse> suggestRecipes(Long userId, List<String> ingredientNames, Pageable pageable) {

        // BƯỚC 1: CHUẨN HÓA INPUT (Shopping List) BẰNG TỪ ĐIỂN ĐỒNG NGHĨA
        Set<String> shoppingListKeys = ingredientNames.stream()
                .map(synonymService::getStandardizedName)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (shoppingListKeys.isEmpty()) {
            return new PageResult<>(pageable.getPageNumber(), pageable.getPageSize(), 0, 0, false, Collections.emptyList());
        }

        // BƯỚC 2: LẤY ỨNG VIÊN TỪ DATABASE
        // Khi query DB, chúng ta vẫn dùng normalize CƠ BẢN, vì DB chứa tên thô.
        List<String> expandedKeys = ingredientNames.stream()
                .map(name -> synonymService.getAllVariants(name))
                .flatMap(List::stream)
                .distinct()
                .toList();

        Pageable candidatesPageable = PageRequest.of(0, CANDIDATE_POOL_SIZE);

        // Query DB bằng danh sách đã mở rộng
        Page<Recipe> candidates = recipeRepository.findCandidateRecipesByIngredients(
                expandedKeys,
                candidatesPageable
        );

        if (candidates.isEmpty()) {
            return new PageResult<>(pageable.getPageNumber(), pageable.getPageSize(), 0, 0, false, Collections.emptyList());
        }

        // BƯỚC 3: CHẤM ĐIỂM (LOGIC MỚI, THAY THẾ GEMINI)
        List<RecipeSuggestionResponse> scoredSuggestions = new ArrayList<>();

        for (Recipe recipe : candidates.getContent()) {
            // 3.1. Lấy dữ liệu phân loại (ĐÃ ĐƯỢC CHUẨN HÓA ĐỒNG NGHĨA LÚC KHỞI ĐỘNG)
            Map<String, IngredientType> classifications = ingredientClassificationService.getClassificationsForRecipe(recipe.getId());

            if (classifications.isEmpty()) {
                continue; // Bỏ qua nếu recipe này chưa được "train"
            }

            long totalMainInRecipe = classifications.values().stream().filter(t -> t == IngredientType.MAIN).count();
            long totalSecondaryInRecipe = classifications.size() - totalMainInRecipe;

            int mainMatchCount = 0;
            int secondaryMatchCount = 0;

            // 3.2. So khớp giỏ hàng (đã chuẩn hóa đồng nghĩa) với dữ liệu phân loại (đã chuẩn hóa đồng nghĩa)
            for (String shoppingKey : shoppingListKeys) {
                IngredientType type = classifications.get(shoppingKey);
                if (type != null) {
                    if (type == IngredientType.MAIN) {
                        mainMatchCount++;
                    } else {
                        secondaryMatchCount++;
                    }
                }
            }

            // 3.3. Tính điểm (CHỈ LẤY CÔNG THỨC CÓ KHỚP NGUYÊN LIỆU CHÍNH)
            if (mainMatchCount > 0) {
                double mainScore = (totalMainInRecipe > 0)
                        ? ((double) mainMatchCount / totalMainInRecipe) * 10.0
                        : 0.0;

                double overallScore = (!classifications.isEmpty())
                        ? ((double) (mainMatchCount + secondaryMatchCount) / classifications.size()) * 10.0
                        : 0.0;

                String justification = String.format("Khớp %d/%d nguyên liệu chính và %d/%d nguyên liệu phụ.",
                        mainMatchCount, totalMainInRecipe,
                        secondaryMatchCount, totalSecondaryInRecipe);

                scoredSuggestions.add(new RecipeSuggestionResponse(
                        RecipeCardResponse.from(recipe),
                        mainScore,
                        overallScore,
                        justification
                ));
            }
        }

        // BƯỚC 4: SẮP XẾP (IN-MEMORY)
        scoredSuggestions.sort(
                Comparator.comparing(RecipeSuggestionResponse::mainIngredientMatchScore)
                        .thenComparing(RecipeSuggestionResponse::overallMatchScore)
                        .reversed()
        );

        // BƯỚC 5: PHÂN TRANG (IN-MEMORY)
        int pageSize = pageable.getPageSize();
        int currentPage = pageable.getPageNumber();
        int startItem = currentPage * pageSize;
        int totalItems = scoredSuggestions.size();

        List<RecipeSuggestionResponse> paginatedList;

        if (startItem < totalItems) {
            int toIndex = Math.min(startItem + pageSize, totalItems);
            paginatedList = scoredSuggestions.subList(startItem, toIndex).stream().limit(3).toList();
        } else {
            paginatedList = Collections.emptyList();
        }

        int totalPages = (int) Math.ceil((double) totalItems / (double) pageSize);
        boolean hasNext = (currentPage + 1) < totalPages;

        // BƯỚC 6: TRẢ VỀ KẾT QUẢ
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
    public ShoppingListResponse uncheckItem(Long userId, Long itemId) {
        // Tải item và đảm bảo nó thuộc về user
        ShoppingList item = shoppingListRepository.findByIdAndUser_UserId(itemId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Mục không tồn tại hoặc không thuộc về bạn: " + itemId));

        // Đánh dấu là false
        item.setChecked(Boolean.FALSE);

        shoppingListRepository.save(item);

        // Trả về response
        Long recipeId = item.getRecipe() != null ? item.getRecipe().getId() : null;
        return toResponse(item, recipeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShoppingListResponse> getItems(Long userId, @Nullable Long recipeId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        List<ShoppingList> items;

        if (recipeId != null) {
            // --- Trường hợp có recipeId ---
            Recipe recipe = recipeRepository.findById(recipeId)
                    .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

            // Kiểm tra quyền xem Recipe
            Long ownerId = recipe.getUser() != null ? recipe.getUser().getUserId() : null;
            if (recipe.getPrivacy() == Privacy.PRIVATE && !Objects.equals(ownerId, userId)) {
                throw new AccessDeniedException("Bạn không có quyền xem shopping list của recipe PRIVATE này.");
            }
            if (recipe.isDeleted()) {
                return Collections.emptyList();
            }

            items = shoppingListRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId);
            items.sort(Comparator.comparing(ShoppingList::getId));

        } else {
            // --- Trường hợp recipeId là null (lấy mục lẻ loi) ---
            items = shoppingListRepository.findByUser_UserIdAndRecipeIsNullOrderByIdDesc(userId);
        }

        // Chuyển đổi sang DTO và trả về
        return items.stream()
                .map(item -> toResponse(item, recipeId)) // recipeId có thể null
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Map<String, Integer> deleteItemsByIds(Long userId, List<Long> itemIds) { // <<< ĐỔI TÊN deleteItems thành deleteItemsByIds
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách ID cần xóa không được rỗng.");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        // Tải các item thuộc user để kiểm tra quyền trước khi xóa
        List<ShoppingList> itemsToDelete = shoppingListRepository.findByIdInAndUser_UserId(itemIds, userId);

        // Kiểm tra xem có ID nào không tìm thấy hoặc không thuộc về user không
        if (itemsToDelete.size() != itemIds.size()) {
            Set<Long> foundIds = itemsToDelete.stream().map(ShoppingList::getId).collect(Collectors.toSet());
            List<Long> missingOrUnauthorizedIds = itemIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new IllegalArgumentException("Không thể xóa các mục sau (không tìm thấy hoặc không có quyền): " + missingOrUnauthorizedIds);
        }

        int deletedCount = itemsToDelete.size();
        if (deletedCount > 0) {
            shoppingListRepository.deleteAllInBatch(itemsToDelete); // Dùng deleteAllInBatch hiệu quả hơn cho nhiều entity
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        return result;
    }


    @Override
    @Transactional
    public Map<String, Integer> deleteItemsByFilter(Long userId, String filter, @Nullable Long recipeId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        int deletedCount;
        String normalizedFilter = filter.toLowerCase().trim();

        switch (normalizedFilter) {
            case "checked":
                deletedCount = shoppingListRepository.deleteCheckedItems(userId);
                break;
            case "recipe":
                if (recipeId == null) {
                    throw new IllegalArgumentException("Cần cung cấp 'recipeId' khi xóa theo 'recipe'.");
                }
                // Kiểm tra xem recipe có tồn tại không (không cần kiểm tra quyền vì chỉ xóa item của user hiện tại)
                recipeRepository.findById(recipeId)
                        .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));
                deletedCount = shoppingListRepository.deleteByRecipeId(userId, recipeId);
                break;
            case "standalone":
                deletedCount = shoppingListRepository.deleteStandaloneItems(userId);
                break;
            case "all":
                deletedCount = shoppingListRepository.deleteAllItems(userId);
                break;
            default:
                throw new IllegalArgumentException("Giá trị 'filter' không hợp lệ: " + filter);
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        return result;
    }
}