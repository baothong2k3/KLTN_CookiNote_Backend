/*
 * @ (#) RecipeServiceImpl.java    1.0    11/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 11/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RecipeCreateRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeIngredientCreate;
import fit.kltn_cookinote_backend.dtos.request.RecipeStepCreate;
import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.CategoryRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.RecipeService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {
    private final RecipeRepository recipeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RecipeResponse createByRecipe(Long id, RecipeCreateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + id));

        Privacy privacy = req.privacy() == null ? Privacy.PRIVATE : req.privacy();

        if (user.getRole() != Role.ADMIN && req.privacy() == Privacy.PUBLIC) {
            throw new AccessDeniedException("Chỉ ADMIN mới được tạo recipe công khai");
        }
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category không tồn tại: " + req.categoryId()));

        Recipe recipe = Recipe.builder()
                .user(user)
                .privacy(privacy)
                .category(category)
                .title(req.title())
                .description(req.description())
                .prepareTime(req.prepareTime())
                .cookTime(req.cookTime())
                .difficulty(req.difficulty())
                .view(0L)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        // ingredients
        List<RecipeIngredient> ingredients = new ArrayList<>();
        for (RecipeIngredientCreate i : req.ingredients()) {
            RecipeIngredient ing = RecipeIngredient.builder()
                    .recipe(recipe)
                    .name(i.name())
                    .quantity(i.quantity())
                    .build();
            ingredients.add(ing);
        }
        recipe.setIngredients(ingredients);

        // steps (chưa có ảnh)
        List<RecipeStep> steps = new ArrayList<>();
        // auto sắp theo stepNo nếu người dùng gửi lộn xộn / null
        int autoIdx = 1;
        List<RecipeStepCreate> sorted = new ArrayList<>(req.steps());
        sorted.sort(Comparator.comparing(s -> s.stepNo() == null ? Integer.MAX_VALUE : s.stepNo()));
        for (RecipeStepCreate s : sorted) {
            Integer stepNo = s.stepNo() != null ? s.stepNo() : autoIdx++;
            RecipeStep step = RecipeStep.builder()
                    .recipe(recipe)
                    .stepNo(stepNo)
                    .content(s.content())
                    .build();
            steps.add(step);
        }
        recipe.setSteps(steps);

        Recipe saved = recipeRepository.saveAndFlush(recipe);
        return RecipeResponse.from(saved);
    }
}
