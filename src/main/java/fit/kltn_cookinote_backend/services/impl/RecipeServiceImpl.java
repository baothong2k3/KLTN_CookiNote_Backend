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
import fit.kltn_cookinote_backend.dtos.request.RecipeUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.CategoryRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepRepository;
import fit.kltn_cookinote_backend.repositories.RecipeIngredientRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.RecipeService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {
    private final RecipeRepository recipeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

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

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> listByOwner(Long ownerUserId, Long viewerUserIdOrNull, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));

        boolean selfView = viewerUserIdOrNull != null && viewerUserIdOrNull.equals(ownerUserId);

        Page<Recipe> pageData = selfView
                ? recipeRepository.findByUser_UserId(ownerUserId, pageable)
                : recipeRepository.findByUser_UserIdAndPrivacyIn(
                ownerUserId,
                EnumSet.of(Privacy.SHARED, Privacy.PUBLIC),
                pageable
        );

        return PageResult.of(pageData.map(RecipeCardResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeStepItem> getSteps(Long viewerUserIdOrNull, Long recipeId) {
        Recipe r = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        Long ownerId = (r.getUser() != null) ? r.getUser().getUserId() : null;
        if (!canView(r.getPrivacy(), ownerId, viewerUserIdOrNull)) {
            throw new AccessDeniedException("Bạn không có quyền xem bước của công thức này.");
        }

        List<RecipeStep> steps = recipeStepRepository.findByRecipe_IdOrderByStepNoAsc(recipeId);
        steps.forEach(s -> {
            if (s.getImages() != null) {
                Hibernate.initialize(s.getImages());
            }
        });

        return steps.stream().map(RecipeStepItem::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeIngredientItem> getIngredients(Long viewerUserIdOrNull, Long recipeId) {
        Recipe r = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        Long ownerId = (r.getUser() != null) ? r.getUser().getUserId() : null;
        if (!canView(r.getPrivacy(), ownerId, viewerUserIdOrNull)) {
            throw new AccessDeniedException("Bạn không có quyền xem thành phần của công thức này.");
        }

        List<RecipeIngredient> ings = recipeIngredientRepository.findByRecipe_IdOrderByIdAsc(recipeId);
        return ings.stream().map(RecipeIngredientItem::from).toList();
    }

    @Override
    @Transactional
    public RecipeResponse updateContent(Long actorUserId, Long recipeId, RecipeUpdateRequest req) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        Long ownerId = recipe.getUser().getUserId();
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // Chỉ ADMIN mới set PUBLIC
        Privacy incomingPrivacy = req.privacy();
        if (incomingPrivacy != null) {
            if (actor.getRole() != Role.ADMIN && incomingPrivacy == Privacy.PUBLIC) {
                throw new AccessDeniedException("Chỉ ADMIN mới được đặt công khai (PUBLIC).");
            }
            recipe.setPrivacy(incomingPrivacy);
        }

        // Update trường cơ bản
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category không tồn tại: " + req.categoryId()));
        recipe.setCategory(category);
        recipe.setTitle(req.title());
        recipe.setDescription(req.description());
        recipe.setPrepareTime(req.prepareTime());
        recipe.setCookTime(req.cookTime());
        recipe.setDifficulty(req.difficulty());
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        // Thay thế toàn bộ ingredients (không ảnh hưởng steps/images)
        if (req.ingredients() != null) {
            List<RecipeIngredient> managed = recipe.getIngredients(); // collection managed
            managed.clear(); // orphanRemoval sẽ xoá các bản ghi cũ an toàn khi flush

            for (RecipeIngredientCreate i : req.ingredients()) {
                RecipeIngredient ing = RecipeIngredient.builder()
                        .recipe(recipe)        // rất quan trọng: set owner!
                        .name(i.name())
                        .quantity(i.quantity())
                        .build();
                managed.add(ing);              // add vào collection đã quản lý
            }
        }

        Recipe saved = recipeRepository.saveAndFlush(recipe);
        return RecipeResponse.from(saved);
    }

    private boolean canView(Privacy privacy, Long ownerId, Long viewerId) {
        return switch (privacy) {
            case PUBLIC, SHARED -> true;
            case PRIVATE -> viewerId != null && viewerId.equals(ownerId);
        };
    }

    private void ensureOwnerOrAdmin(Long actorId, Long ownerId, Role actorRole) {
        if (actorRole == Role.ADMIN) return;
        if (!Objects.equals(actorId, ownerId)) {
            throw new AccessDeniedException("Chỉ chủ sở hữu hoặc ADMIN mới được chỉnh sửa.");
        }
    }
}
