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

import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.*;
import fit.kltn_cookinote_backend.services.CloudinaryService;
import fit.kltn_cookinote_backend.services.RecipeService;
import fit.kltn_cookinote_backend.utils.ShoppingListUtils;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {
    private final RecipeRepository recipeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final CloudinaryService cloudinaryService;
    private final ShoppingListRepository shoppingListRepository;
    private final FavoriteRepository favoriteRepository;

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
        if (req.steps() != null && !req.steps().isEmpty()) {
            // sort input theo stepNo (null xuống cuối) để giữ ý người dùng tối đa
            List<RecipeStepCreate> sorted = new ArrayList<>(req.steps());
            sorted.sort(Comparator.comparing(s -> s.stepNo() == null ? Integer.MAX_VALUE : s.stepNo()));

            List<RecipeStep> steps = new ArrayList<>(sorted.size());
            Set<Integer> used = new HashSet<>();
            int autoIdx = 1;

            for (RecipeStepCreate s : sorted) {
                Integer desired = s.stepNo();

                // chuẩn hoá: nếu null/<=0/trùng thì gán số tự động tiếp theo chưa dùng
                if (desired == null || desired <= 0 || used.contains(desired)) {
                    while (used.contains(autoIdx)) autoIdx++;
                    desired = autoIdx++;
                }

                used.add(desired);

                steps.add(RecipeStep.builder()
                        .recipe(recipe)
                        .stepNo(desired)
                        .suggestedTime(s.suggestedTime())
                        .tips(s.tips())
                        .content(s.content())
                        .build());
            }
            recipe.setSteps(steps);
        } else {
            // không có bước nào: để list trống
            recipe.setSteps(new ArrayList<>());
        }

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

        // *** KIỂM TRA TRẠNG THÁI DELETED ***
        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Không thể cập nhật công thức đã bị xóa: " + recipeId);
        }

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

    @Override
    @Transactional
    public void deleteRecipe(Long actorUserId, Long recipeId) { // soft delete
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // *** KIỂM TRA TRẠNG THÁI DELETED ***
        if (recipe.isDeleted()) {
            throw new IllegalStateException("Công thức này đã được xóa trước đó.");
        }

        Long ownerId = recipe.getUser().getUserId();
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // Soft delete the recipe
        recipe.setDeleted(true);
        recipe.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));

        // Deactivate all images
        if (recipe.getSteps() != null) {
            for (RecipeStep step : recipe.getSteps()) {
                if (step.getImages() != null) {
                    for (RecipeStepImage image : step.getImages()) {
                        image.setActive(false);
                    }
                }
            }
        }
        recipeRepository.save(recipe);

        // CẬP NHẬT: Đánh dấu các shopping list item liên quan
        List<ShoppingList> relatedItems = shoppingListRepository.findByRecipe_Id(recipeId);
        if (!relatedItems.isEmpty()) {
            for (ShoppingList item : relatedItems) {
                item.setIsRecipeDeleted(true);
                //item.setOriginalRecipeTitle(recipe.getTitle());
            }
            shoppingListRepository.saveAll(relatedItems);
        }
    }

    /**
     * Xem danh sách công thức đã xoá (chỉ ADMIN hoặc chủ sở hữu)
     *
     * @param actor
     * @param filterUserId
     * @param page
     * @param size
     * @return
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> listDeletedRecipes(User actor, Long filterUserId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "deletedAt"));

        Page<Recipe> pageData;

        if (actor.getRole() == Role.ADMIN) {
            pageData = recipeRepository.findDeleted(filterUserId, pageable);
        } else {
            pageData = recipeRepository.findDeleted(actor.getUserId(), pageable);
        }

        return PageResult.of(pageData.map(RecipeCardResponse::from));
    }

    @Override
    @Transactional
    public void hardDeleteRecipe(Long actorUserId, Long recipeId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findDeletedById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Công thức không tồn tại hoặc chưa được xóa mềm: " + recipeId));

        Long ownerId = recipe.getUser().getUserId();
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // CẬP NHẬT: Xử lý các shopping list item trước khi xóa recipe
        List<ShoppingList> relatedItems = shoppingListRepository.findByRecipe_Id(recipeId);
        if (!relatedItems.isEmpty()) {
            final String recipeTitle = recipe.getTitle();
            for (ShoppingList item : relatedItems) {
                item.setRecipe(null); // Ngắt kết nối
                item.setOriginalRecipeTitle(recipeTitle); // Lưu lại tên
                item.setIsRecipeDeleted(true); // Đánh dấu đã xóa
            }
            shoppingListRepository.saveAllAndFlush(relatedItems);
        }

        // CẬP NHẬT: Xử lý các favorite item trước khi xóa recipe
        List<Favorite> relatedFavorites = favoriteRepository.findByRecipe_Id(recipeId);
        if (!relatedFavorites.isEmpty()) {
            final String recipeTitle = recipe.getTitle();
            for (Favorite favorite : relatedFavorites) {
                favorite.setRecipe(null);
                favorite.setOriginalRecipeTitle(recipeTitle);
                favorite.setIsRecipeDeleted(true);
            }
            favoriteRepository.saveAllAndFlush(relatedFavorites);
        }

        // Thu thập tất cả public ID của ảnh để xóa sau khi commit DB
        final List<String> publicIdsToDelete = new ArrayList<>();

        // 1. Ảnh bìa từ lịch sử
        if (recipe.getCoverImageHistory() != null) {
            recipe.getCoverImageHistory().stream()
                    .map(RecipeCoverImageHistory::getImageUrl)
                    .filter(StringUtils::hasText)
                    .map(cloudinaryService::extractPublicIdFromUrl)
                    .filter(StringUtils::hasText)
                    .forEach(publicIdsToDelete::add);
        }

        // 2. Ảnh của các bước
        if (recipe.getSteps() != null) {
            for (RecipeStep step : recipe.getSteps()) {
                if (step.getImages() != null) {
                    step.getImages().stream()
                            .map(RecipeStepImage::getImageUrl)
                            .filter(StringUtils::hasText)
                            .map(cloudinaryService::extractPublicIdFromUrl)
                            .filter(StringUtils::hasText)
                            .forEach(publicIdsToDelete::add);
                }
            }
        }

        // Đăng ký một callback để xóa ảnh trên Cloudinary CHỈ KHI giao dịch DB thành công
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String publicId : publicIdsToDelete) {
                    cloudinaryService.safeDeleteByPublicId(publicId);
                }
            }
        });

        // Xóa recipe khỏi DB
        recipeRepository.delete(recipe);
    }

    @Override
    @Transactional
    public RecipeResponse forkRecipe(Long clonerUserId, Long originalRecipeId, ForkRecipeRequest req) {
        User cloner = userRepository.findById(clonerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + clonerUserId));

        Recipe originalRecipe = recipeRepository.findDetailById(originalRecipeId)
                .orElseThrow(() -> new EntityNotFoundException("Công thức gốc không tồn tại: " + originalRecipeId));

        // 1. Kiểm tra quyền xem công thức gốc
        Long ownerId = originalRecipe.getUser().getUserId();
        if (!canView(originalRecipe.getPrivacy(), ownerId, clonerUserId)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này để sao chép.");
        }

        // 2. Không cho tự fork công thức của chính mình
        if (Objects.equals(clonerUserId, ownerId)) {
            throw new IllegalArgumentException("Bạn không thể sao chép công thức của chính mình.");
        }

        // 3. Người dùng không được set PUBLIC nếu không phải ADMIN
        if (cloner.getRole() != Role.ADMIN && req.privacy() == Privacy.PUBLIC) {
            throw new AccessDeniedException("Chỉ ADMIN mới được tạo công thức công khai.");
        }

        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category không tồn tại: " + req.categoryId()));

        // 4. Tạo công thức mới dựa trên request
        Recipe newRecipe = Recipe.builder()
                .user(cloner) // Chủ sở hữu là người đang fork
                .originalRecipe(originalRecipe) // Liên kết tới công thức gốc
                .category(category)
                .title(req.title())
                .description(req.description())
                .prepareTime(req.prepareTime())
                .cookTime(req.cookTime())
                .difficulty(req.difficulty())
                .privacy(req.privacy()) // Theo lựa chọn của người dùng
                .view(0L) // Bắt đầu từ 0
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        // 5. Sao chép và thêm nguyên liệu, các bước từ request
        List<RecipeIngredient> ingredients = req.ingredients().stream()
                .map(i -> RecipeIngredient.builder()
                        .recipe(newRecipe)
                        .name(i.name())
                        .quantity(i.quantity())
                        .build())
                .collect(Collectors.toList());
        newRecipe.setIngredients(ingredients);

        List<RecipeStep> steps = req.steps().stream()
                .map(s -> RecipeStep.builder()
                        .recipe(newRecipe)
                        .stepNo(s.stepNo())
                        .content(s.content())
                        .suggestedTime(s.suggestedTime())
                        .tips(s.tips())
                        // Lưu ý: Ảnh không được sao chép trực tiếp, người dùng phải tự upload cho công thức mới của họ
                        .images(new ArrayList<>())
                        .build())
                .collect(Collectors.toList());
        newRecipe.setSteps(steps);

        Recipe saved = recipeRepository.save(newRecipe);
        return RecipeResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> searchPublicRecipes(String query, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Recipe> pageData = recipeRepository.searchPublicRecipes(query, pageable);

        return PageResult.of(pageData.map(RecipeCardResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> listPopular(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "view"));

        Page<Recipe> pageData = recipeRepository.findByPrivacyAndDeletedFalseOrderByViewDesc(Privacy.PUBLIC, pageable);
        return PageResult.of(pageData.map(RecipeCardResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> listEasyToCook(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s);

        Page<Recipe> pageData = recipeRepository.findEasyToCook(pageable);
        return PageResult.of(pageData.map(RecipeCardResponse::from));
    }

    @Override
    @Transactional
    public RecipeResponse addIngredients(Long actorUserId, Long recipeId, AddIngredientsRequest req) {
        // 1) Tải User (actor) và Recipe, kiểm tra quyền
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // Kiểm tra Recipe đã bị xóa chưa
        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Không thể thêm nguyên liệu vào công thức đã bị xóa: " + recipeId);
        }

        Long ownerId = recipe.getUser().getUserId();
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // 2) Lấy danh sách nguyên liệu hiện có (dùng Set để kiểm tra trùng lặp hiệu quả)
        // Sử dụng tên đã chuẩn hóa làm key để tránh trùng lặp không phân biệt chữ hoa/thường, khoảng trắng
        Set<String> existingNormalizedNames = recipe.getIngredients().stream()
                .map(RecipeIngredient::getName)
                .filter(Objects::nonNull)
                .map(ShoppingListUtils::normalize) // Chuẩn hóa tên (toLowerCase, trim, gộp khoảng trắng)
                .collect(Collectors.toSet());

        // 3) Xử lý thêm nguyên liệu mới
        List<RecipeIngredient> ingredientsToAdd = new ArrayList<>();
        int addedCount = 0;
        for (RecipeIngredientCreate newIngDto : req.ingredients()) {
            String normalizedNewName = ShoppingListUtils.normalize(newIngDto.name());

            // Bỏ qua nếu tên trống hoặc đã tồn tại (sau chuẩn hóa)
            if (normalizedNewName.isEmpty() || existingNormalizedNames.contains(normalizedNewName)) {
                continue; // Bỏ qua nguyên liệu này
            }

            // Tạo entity mới và liên kết với Recipe
            RecipeIngredient newIngredient = RecipeIngredient.builder()
                    .recipe(recipe) // Liên kết với recipe hiện tại
                    .name(ShoppingListUtils.canonicalize(newIngDto.name())) // Lưu tên đã chuẩn hóa (trim, gộp khoảng trắng)
                    .quantity(ShoppingListUtils.canonicalize(newIngDto.quantity())) // Chuẩn hóa quantity
                    .build();

            ingredientsToAdd.add(newIngredient);
            existingNormalizedNames.add(normalizedNewName); // Thêm vào set để kiểm tra các mục tiếp theo trong request
            addedCount++;
        }

        // 4) Chỉ cập nhật nếu thực sự có nguyên liệu mới được thêm
        if (addedCount > 0) {
            // Thêm các nguyên liệu hợp lệ vào collection của Recipe
            recipe.getIngredients().addAll(ingredientsToAdd);

            // Cập nhật thời gian
            recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

            // Lưu Recipe (CascadeType.ALL sẽ tự động lưu các ingredientsToAdd)
            recipeRepository.saveAndFlush(recipe);
        } else {
            // Nếu không có gì để thêm (do trùng lặp hoặc rỗng), không cần save lại recipe
            // Chỉ cần trả về trạng thái hiện tại
            // Tải lại để đảm bảo dữ liệu mới nhất (phòng trường hợp có thay đổi khác)
            recipe = recipeRepository.findDetailById(recipeId)
                    .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));
            return RecipeResponse.from(recipe);
        }


        // 5) Tải lại Recipe chi tiết để trả về (đã bao gồm các nguyên liệu mới)
        Recipe reloaded = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại sau khi cập nhật: " + recipeId)); // Nên có

        return RecipeResponse.from(reloaded);
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
