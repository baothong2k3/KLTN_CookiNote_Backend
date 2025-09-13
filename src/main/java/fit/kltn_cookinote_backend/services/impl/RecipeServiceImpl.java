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
import fit.kltn_cookinote_backend.dtos.response.PageResult;
import fit.kltn_cookinote_backend.dtos.response.RecipeCardResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 12; // mobile-friendly
    private static final int MAX_SIZE = 20;

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

    @Override
    @Transactional
    public RecipeResponse getDetail(Long viewerUserIdOrNull, Long recipeId) {
        Recipe recipe = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        Long ownerId = (recipe.getUser() != null) ? recipe.getUser().getUserId() : null;
        boolean isOwner = (viewerUserIdOrNull != null) && viewerUserIdOrNull.equals(ownerId);

        if (!canView(recipe.getPrivacy(), ownerId, viewerUserIdOrNull)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này.");
        }

        if (!isOwner) {
            recipeRepository.incrementViewById(recipeId);
            recipe.setView((recipe.getView() == null ? 0 : recipe.getView()) + 1);
        }

        return RecipeResponse.from(recipe);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> listPublicByCategory(Long categoryId, int page, int size) {
        if (categoryId == null) throw new EntityNotFoundException("Category không hợp lệ.");

        // Chuẩn hóa phân trang cho mobile
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Recipe> pageData = recipeRepository.findByCategory_IdAndPrivacy(categoryId, Privacy.PUBLIC, pageable);

        Page<RecipeCardResponse> mapped = pageData.map(RecipeCardResponse::from);
        return PageResult.of(mapped);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> listPublic(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Recipe> pageData = recipeRepository.findByPrivacy(Privacy.PUBLIC, pageable);
        Page<RecipeCardResponse> mapped = pageData.map(RecipeCardResponse::from);
        return PageResult.of(mapped);
    }

    private boolean canView(Privacy privacy, Long ownerId, Long viewerId) {
        return switch (privacy) {
            case PUBLIC, SHARED -> true;
            case PRIVATE -> viewerId != null && viewerId.equals(ownerId);
        };
    }
}
