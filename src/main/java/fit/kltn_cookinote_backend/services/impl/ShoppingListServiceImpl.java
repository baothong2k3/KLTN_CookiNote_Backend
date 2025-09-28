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

import fit.kltn_cookinote_backend.dtos.response.ShoppingListResponse;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeIngredient;
import fit.kltn_cookinote_backend.entities.ShoppingList;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.RecipeIngredientRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.ShoppingListRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.ShoppingListService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static fit.kltn_cookinote_backend.utils.ShoppingListUtils.*;

@Service
@RequiredArgsConstructor
public class ShoppingListServiceImpl implements ShoppingListService {

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final ShoppingListRepository shoppingListRepository;

    @Override
    @Transactional
    public List<ShoppingListResponse> createFromRecipe(Long userId, Long recipeId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // Quyền xem
        if (recipe.getPrivacy() == Privacy.PRIVATE && !Objects.equals(recipe.getUser().getUserId(), userId)) {
            throw new AccessDeniedException("Bạn không có quyền sử dụng recipe PRIVATE này.");
        }


        // Lấy danh sách ShoppingList hiện có (KHÔNG xoá)
        List<ShoppingList> existing = shoppingListRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId);
        Map<String, ShoppingList> existByKey = existing.stream()
                .filter(it -> it.getIngredient() != null)
                .collect(Collectors.toMap(
                        it -> normalize(it.getIngredient()),
                        it -> it,
                        (a, b) -> a
                ));

        // Lấy toàn bộ RecipeIngredient
        List<RecipeIngredient> ingredients = ingredientRepository.findByRecipe_IdOrderByIdAsc(recipeId);

        List<ShoppingList> toCreate = new ArrayList<>();
        for (RecipeIngredient ri : ingredients) {
            // DÙNG TRỰC TIẾP getter
            String name = ri.getName();
            if (name == null || normalize(name).isEmpty()) continue;

            String qty = ri.getQuantity();
            String key = normalize(name);

            ShoppingList exist = existByKey.get(key);
            if (exist != null) {
                // Giữ checked cũ, chỉ cập nhật quantity nếu khác
                if (!Objects.equals(safe(qty), safe(exist.getQuantity()))) {
                    exist.setQuantity(qty); // dirty checking sẽ tự flush
                }
            } else {
                // Thêm mới, checked=false
                toCreate.add(ShoppingList.builder()
                        .user(user)
                        .recipe(recipe)
                        .ingredient(canonicalize(name))
                        .quantity(qty)
                        .checked(Boolean.FALSE)
                        .build());
            }
        }

        if (!toCreate.isEmpty()) shoppingListRepository.saveAll(toCreate);

        // Trả về toàn bộ list sau merge (bao gồm cả các mục tự thêm từ trước)
        return shoppingListRepository.findByUser_UserIdAndRecipe_Id(userId, recipeId).stream()
                .map(s -> toResponse(s, recipeId)).toList();
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
        if (name.isEmpty()) throw new IllegalArgumentException("Tên nguyên liệu trống.");

        // Upsert theo (user, recipe_id, ingredient ignoreCase)
        ShoppingList item = shoppingListRepository
                .findByUser_UserIdAndRecipe_IdAndIngredientIgnoreCase(userId, recipeId, name)
                .map(exist -> {
                    // GIỮ checked cũ; chỉ cập nhật quantity nếu khác
                    if (!Objects.equals(safe(quantity), safe(exist.getQuantity()))) {
                        exist.setQuantity(quantity);
                    }
                    return exist;
                })
                .orElseGet(() -> shoppingListRepository.save(
                        ShoppingList.builder()
                                .user(user)
                                .recipe(recipe)
                                .ingredient(name)              // canonicalized
                                .quantity(quantity)
                                .checked(Boolean.FALSE)
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

                // Xoá item hiện tại để tránh duplicate
                shoppingListRepository.delete(current);

                return toResponse(target, recipeId);
            }
        }

        // Không cần merge -> cập nhật trực tiếp current
        current.setIngredient(nameNew); // canonicalized
        if (qtyNew != null) current.setQuantity(qtyNew);
        if (newCheckedOrNull != null) current.setChecked(newCheckedOrNull);

        return toResponse(current, recipeId);
    }

    @Override
    @Transactional
    public ShoppingListResponse moveItem(Long userId, Long itemId, Long targetRecipeIdOrNull) {
        // ensure user
        userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + userId));

        // item hiện tại (thuộc về user)
        ShoppingList current = shoppingListRepository.findByIdAndUser_UserId(itemId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Item không tồn tại hoặc không thuộc về bạn: " + itemId));

        // list nguồn/đích
        Long sourceRecipeId = current.getRecipe() != null ? current.getRecipe().getId() : null;

        // nếu không đổi list -> no-op
        if (Objects.equals(sourceRecipeId, targetRecipeIdOrNull)) {
            return toResponse(current, sourceRecipeId);
        }

        Recipe targetRecipe = null;
        if (targetRecipeIdOrNull != null) {
            targetRecipe = recipeRepository.findById(targetRecipeIdOrNull)
                    .orElseThrow(() -> new EntityNotFoundException("Recipe đích không tồn tại: " + targetRecipeIdOrNull));

            // chỉ owner mới được chuyển vào recipe PRIVATE
            if (targetRecipe.getPrivacy() == Privacy.PRIVATE
                    && !Objects.equals(targetRecipe.getUser().getUserId(), userId)) {
                throw new AccessDeniedException("Bạn không có quyền chuyển vào recipe PRIVATE này.");
            }
        }

        // tên đã lưu của current đã canonicalized từ trước
        String name = current.getIngredient();

        // tìm trùng trong list đích
        Optional<ShoppingList> dup = (targetRecipe == null)
                ? shoppingListRepository.findByUser_UserIdAndRecipeIsNullAndIngredientIgnoreCase(userId, name)
                : shoppingListRepository.findByUser_UserIdAndRecipe_IdAndIngredientIgnoreCase(userId, targetRecipe.getId(), name);

        if (dup.isPresent() && !dup.get().getId().equals(current.getId())) {
            // MERGE vào item đích
            ShoppingList target = dup.get();

            boolean mergedChecked = Boolean.TRUE.equals(target.getChecked()) || Boolean.TRUE.equals(current.getChecked());
            String mergedQty = (target.getQuantity() != null) ? target.getQuantity() : current.getQuantity();

            target.setChecked(mergedChecked);
            target.setQuantity(mergedQty);
            // target giữ nguyên ingredient (đã canonical)

            // xoá bản gốc
            shoppingListRepository.delete(current);

            return toResponse(target, targetRecipeIdOrNull);
        } else {
            // không trùng -> chỉ chuyển list
            current.setRecipe(targetRecipe);
            return toResponse(current, targetRecipeIdOrNull);
        }
    }
}
