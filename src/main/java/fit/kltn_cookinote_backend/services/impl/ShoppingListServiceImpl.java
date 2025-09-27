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
}
