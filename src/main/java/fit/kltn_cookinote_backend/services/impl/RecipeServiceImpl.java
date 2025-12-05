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

import com.cloudinary.Cloudinary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fit.kltn_cookinote_backend.dtos.request.*;
import fit.kltn_cookinote_backend.dtos.response.*;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.*;
import fit.kltn_cookinote_backend.services.*;
import fit.kltn_cookinote_backend.utils.CloudinaryUtils;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import fit.kltn_cookinote_backend.utils.ShoppingListUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeServiceImpl implements RecipeService {
    private final RecipeRepository recipeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final CloudinaryService cloudinaryService;
    private final ShoppingListRepository shoppingListRepository;
    private final FavoriteRepository favoriteRepository;
    private final CookedHistoryRepository cookedHistoryRepository;
    private final RecipeRatingRepository ratingRepository;
    private final CommentService commentService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Cloudinary cloudinary;
    private final RecipeStepImageRepository stepImageRepository;
    private final RecipeCoverImageHistoryRepository coverImageHistoryRepository;
    private final AiRecipeService aiRecipeService;
    private final GeminiApiClient geminiApiClient;

    @Value("${app.cloudinary.recipe-folder}")
    private String recipeFolder;

    @PersistenceContext
    private EntityManager em;

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 12; // mobile-friendly
    private static final int MAX_SIZE = 20;

    // --- HELPER 1: TÁCH TỪ PHẦN DUPLICATE TẠO RECIPE ---

    /**
     * Xây dựng entity Recipe (chưa lưu) từ Request và User.
     * Đã bao gồm kiểm tra quyền Privacy và xử lý Ingredients.
     */
    private Recipe buildRecipeEntity(User user, RecipeCreateRequest req) {
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
                .averageRating(0.0)
                .ratingCount(0)
                .commentCount(0)
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

        return recipe; // Trả về entity chưa được lưu
    }
    // --- HẾT HELPER 1 ---

    // --- HELPER 2: TÁCH TỪ PHẦN DUPLICATE XỬ LÝ STEPS ---

    /**
     * Chuẩn hóa (sort, gán stepNo) và xây dựng danh sách RecipeStep cho Recipe.
     *
     * @param recipe        Recipe (chưa lưu) để gán liên kết.
     * @param stepReqs      Danh sách step DTO từ request.
     * @param stepsRequired Nếu true, ném lỗi nếu stepReqs rỗng.
     */
    private void normalizeAndBuildSteps(Recipe recipe, List<RecipeStepCreate> stepReqs, boolean stepsRequired) {
        if (stepReqs == null || stepReqs.isEmpty()) {
            if (stepsRequired) {
                throw new IllegalArgumentException("Danh sách bước nấu không được rỗng.");
            }
            // không có bước nào: để list trống
            recipe.setSteps(new ArrayList<>());
            return;
        }

        // sort input theo stepNo (null xuống cuối) để giữ ý người dùng tối đa
        List<RecipeStepCreate> sorted = new ArrayList<>(stepReqs);
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
                    .images(new ArrayList<>()) // Luôn khởi tạo list rỗng
                    .build());
        }
        recipe.setSteps(steps);
    }
    // --- HẾT HELPER 2 ---


    @Override
    @Transactional
    public RecipeResponse createRecipeFull(Long actorUserId, String recipeJson,
                                           MultipartFile coverImage,
                                           Map<String, List<MultipartFile>> allStepImages) throws IOException {

        // 1. Deserialize và Validate JSON
        RecipeCreateRequest req;
        try {
            req = objectMapper.readValue(recipeJson, RecipeCreateRequest.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Dữ liệu JSON 'recipe' không hợp lệ: " + e.getMessage());
        }
        Set<ConstraintViolation<RecipeCreateRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        // 2. Tạo Recipe (SỬ DỤNG HELPER 1)
        User user = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = buildRecipeEntity(user, req);

        // 3. Xử lý Steps (SỬ DỤNG HELPER 2)
        // (true = bắt buộc phải có steps)
        normalizeAndBuildSteps(recipe, req.steps(), true);

        // 4. Lưu Recipe lần 1 (để lấy ID cho recipe và các steps)
        Recipe savedRecipe = recipeRepository.saveAndFlush(recipe);
        // LOG INFO: Tạo mới
        log.info("Recipe Created: ID={}, Title='{}', UserID={}",
                savedRecipe.getId(), savedRecipe.getTitle(), actorUserId);

        Long recipeId = savedRecipe.getId();

        // Sắp xếp lại các bước đã lưu để truy cập bằng index
        List<RecipeStep> savedSteps = new ArrayList<>(savedRecipe.getSteps());
        savedSteps.sort(Comparator.comparing(RecipeStep::getStepNo));
        int numSteps = savedSteps.size();

        // 5. Xử lý Ảnh Steps (Pha 2b) - Logic này giữ nguyên
        List<RecipeStepImage> allImagesToSave = new ArrayList<>();
        if (allStepImages != null && !allStepImages.isEmpty()) {
            for (Map.Entry<String, List<MultipartFile>> entry : allStepImages.entrySet()) {
                String key = entry.getKey(); // "stepImages_1"
                List<MultipartFile> files = entry.getValue();

                if (files == null || files.isEmpty()) continue;

                int stepIndex;
                try {
                    stepIndex = Integer.parseInt(key.substring("stepImages_".length()));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Key ảnh không hợp lệ (phải có dạng stepImages_N): " + key);
                }

                if (stepIndex <= 0) continue;
                if (stepIndex > numSteps) {
                    throw new IllegalArgumentException("Dữ liệu ảnh '" + key + "' không hợp lệ vì công thức chỉ có " + numSteps + " bước.");
                }

                if (files.size() > 5) {
                    throw new IllegalArgumentException("Bước " + stepIndex + " (" + key + ") có " + files.size() + " ảnh. Tối đa 5 ảnh/bước.");
                }

                RecipeStep targetStep = savedSteps.get(stepIndex - 1);
                int imgIdx = 1;

                for (MultipartFile file : files) {
                    ImageValidationUtils.validateImage(file);
                    String publicId = recipeFolder + "/r_" + recipeId + "/s_" + targetStep.getId() + "/img_" + Instant.now().getEpochSecond() + "_" + (imgIdx++);
                    String url = CloudinaryUtils.uploadImage(cloudinary, file, recipeFolder, publicId);

                    allImagesToSave.add(RecipeStepImage.builder()
                            .step(targetStep)
                            .imageUrl(url)
                            .build());
                }
            }
        }

        if (!allImagesToSave.isEmpty()) {
            stepImageRepository.saveAll(allImagesToSave);
        }

        // 6. Xử lý Ảnh Cover (Pha 2a) - Logic này giữ nguyên
        if (coverImage != null && !coverImage.isEmpty()) {
            ImageValidationUtils.validateImage(coverImage);
            String publicId = recipeFolder + "/r_" + recipeId + "/cover_" + Instant.now().getEpochSecond();
            String newUrl = CloudinaryUtils.uploadImage(cloudinary, coverImage, recipeFolder, publicId);

            savedRecipe.setImageUrl(newUrl);

            RecipeCoverImageHistory historyRecord = RecipeCoverImageHistory.builder()
                    .recipe(savedRecipe)
                    .imageUrl(newUrl)
                    .active(true)
                    .build();
            coverImageHistoryRepository.save(historyRecord);

            recipeRepository.save(savedRecipe);
        }

        // 7. Flush + Clear cache - Logic này giữ nguyên
        em.flush();
        em.clear();

        // Vì hàm này là @Async, nó sẽ trả về ngay lập tức, không block API
        aiRecipeService.updateNutritionBackground(savedRecipe.getId());

        // 8. Tải lại đầy đủ (bao gồm cả ảnh) và Trả về
        Recipe finalRecipe = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Lỗi khi tải lại recipe: " + recipeId));

        return buildRecipeResponse(finalRecipe, actorUserId);
    }

    @Override
    @Transactional
    public RecipeResponse createByRecipe(Long id, RecipeCreateRequest req) {
        // 1. Tải User
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + id));

        // 2. Tạo Recipe (SỬ DỤNG HELPER 1)
        Recipe recipe = buildRecipeEntity(user, req);

        // 3. Xử lý Steps (SỬ DỤNG HELPER 2)
        // (false = không bắt buộc có steps)
        normalizeAndBuildSteps(recipe, req.steps(), false);

        // 4. Lưu và trả về
        Recipe saved = recipeRepository.saveAndFlush(recipe);

        // Vì hàm này là @Async, nó sẽ trả về ngay lập tức, không block API
        aiRecipeService.updateNutritionBackground(saved.getId());
        return RecipeResponse.from(saved, false, null, List.of());
    }

    @Override
    @Transactional
    public RecipeResponse getDetail(Long viewerUserId, Long recipeId) {
        // 1. Tìm Recipe
        Recipe recipe = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // 2. Xác định Owner và Viewer
        Long ownerId = (recipe.getUser() != null) ? recipe.getUser().getUserId() : null;
        boolean isOwner = (viewerUserId != null && viewerUserId.equals(ownerId));

        // 3. Kiểm tra quyền Admin
        boolean isAdmin = false;
        if (viewerUserId != null) {
            // Tải thông tin người xem để check Role
            User viewer = userRepository.findById(viewerUserId).orElse(null);
            if (viewer != null && viewer.getRole() == Role.ADMIN) {
                isAdmin = true;
            }
        }

        // 4. Logic kiểm tra quyền truy cập:
        // Cho phép xem nếu:
        // - Công thức là PUBLIC/SHARED (check bởi canView)
        // - HOẶC người xem là Chủ sở hữu
        // - HOẶC người xem là ADMIN
        if (!isAdmin && !canView(recipe.getPrivacy(), ownerId, viewerUserId)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này.");
        }

        // 5. Tăng lượt xem (chỉ tăng nếu người xem KHÔNG phải là chủ sở hữu)
        if (!isOwner) {
            recipeRepository.incrementViewById(recipeId);
            recipe.setView((recipe.getView() == null ? 0 : recipe.getView()) + 1);
        }

        // 6. Trả về response
        return buildRecipeResponse(recipe, viewerUserId);
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

        return buildRecipeResponse(saved, actorUserId);
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
        // LOG INFO: Xóa mềm
        log.info("Recipe Soft-Deleted: ID={} by UserID={}", recipeId, actorUserId);
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

        // Đánh dấu các shopping list item liên quan
        List<ShoppingList> relatedItems = shoppingListRepository.findByRecipe_Id(recipeId);
        if (!relatedItems.isEmpty()) {
            for (ShoppingList item : relatedItems) {
                item.setIsRecipeDeleted(true);
            }
            shoppingListRepository.saveAll(relatedItems);
        }

        cookedHistoryRepository.markAsDeletedByRecipeId(recipeId, recipe.getTitle());
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

        final String recipeTitle = recipe.getTitle();

        // Xử lý các shopping list item trước khi xóa recipe
        List<ShoppingList> relatedItems = shoppingListRepository.findByRecipe_Id(recipeId);
        if (!relatedItems.isEmpty()) {
            for (ShoppingList item : relatedItems) {
                item.setRecipe(null); // Ngắt kết nối
                item.setOriginalRecipeTitle(recipeTitle); // Lưu lại tên
                item.setIsRecipeDeleted(true); // Đánh dấu đã xóa
            }
            shoppingListRepository.saveAllAndFlush(relatedItems);
        }

        // Xử lý các favorite item trước khi xóa recipe
        List<Favorite> relatedFavorites = favoriteRepository.findByRecipe_Id(recipeId);
        if (!relatedFavorites.isEmpty()) {
            for (Favorite favorite : relatedFavorites) {
                favorite.setRecipe(null);
                favorite.setOriginalRecipeTitle(recipeTitle);
                favorite.setIsRecipeDeleted(true);
            }
            favoriteRepository.saveAllAndFlush(relatedFavorites);
        }

        // Xử lý các CookedHistory item
        List<CookedHistory> relatedCookedHistories = cookedHistoryRepository.findByRecipe_Id(recipeId);
        if (!relatedCookedHistories.isEmpty()) {
            for (CookedHistory history : relatedCookedHistories) {
                history.setRecipe(null); // Ngắt kết nối
                history.setOriginalRecipeTitle(recipeTitle); // Lưu lại tên
                history.setIsRecipeDeleted(true); // Đánh dấu đã xóa
            }
            cookedHistoryRepository.saveAllAndFlush(relatedCookedHistories); // <<< Lưu thay đổi
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
                .averageRating(0.0)
                .ratingCount(0)
                .commentCount(0)
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
        return RecipeResponse.from(saved, false, null, List.of());
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
    public List<RecipeIngredientItem> addIngredients(Long actorUserId, Long recipeId, AddIngredientsRequest req) {
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

        // 2) Tạo Map các nguyên liệu hiện có: Key = Tên chuẩn hóa, Value = Entity
        Map<String, RecipeIngredient> existingIngredientsMap = recipe.getIngredients().stream()
                .collect(Collectors.toMap(
                        ing -> ShoppingListUtils.normalize(ing.getName()), // Key: tên đã chuẩn hóa
                        Function.identity(),                               // Value: chính object đó
                        (existing, replacement) -> existing                // Nếu DB lỡ có trùng, giữ cái đầu tiên
                ));

        // 3) Xử lý danh sách gửi lên (Upsert logic)
        boolean isChanged = false;
        List<RecipeIngredient> ingredientsToAdd = new ArrayList<>();

        for (RecipeIngredientCreate newIngDto : req.ingredients()) {
            String rawName = newIngDto.name();
            if (rawName == null || rawName.isBlank()) continue;

            String normalizedKey = ShoppingListUtils.normalize(rawName);
            String canonicalName = ShoppingListUtils.canonicalize(rawName);
            String canonicalQty = ShoppingListUtils.canonicalize(newIngDto.quantity());

            if (existingIngredientsMap.containsKey(normalizedKey)) {
                // --- TRƯỜNG HỢP ĐÃ TỒN TẠI: CẬP NHẬT ---
                RecipeIngredient existingItem = existingIngredientsMap.get(normalizedKey);

                // Kiểm tra xem có thay đổi gì không (số lượng hoặc cách viết tên)
                boolean qtyChanged = !Objects.equals(existingItem.getQuantity(), canonicalQty);

                // (Tùy chọn) Cập nhật lại tên theo cách viết mới nhất của user (ví dụ: "đường" -> "Đường")
                // Nếu muốn giữ tên cũ trong DB thì bỏ dòng setName này đi.
                boolean nameChanged = !existingItem.getName().equals(canonicalName);

                if (qtyChanged || nameChanged) {
                    existingItem.setQuantity(canonicalQty);
                    existingItem.setName(canonicalName);
                    isChanged = true;
                }
            } else {
                // --- TRƯỜNG HỢP CHƯA CÓ: THÊM MỚI ---

                // Kiểm tra xem trong danh sách chờ thêm (ingredientsToAdd) đã có món này chưa
                // (tránh trường hợp request gửi lên 2 dòng "đường" mới cùng lúc)
                boolean alreadyInQueue = ingredientsToAdd.stream()
                        .anyMatch(i -> ShoppingListUtils.normalize(i.getName()).equals(normalizedKey));

                if (!alreadyInQueue) {
                    RecipeIngredient newIngredient = RecipeIngredient.builder()
                            .recipe(recipe)
                            .name(canonicalName)
                            .quantity(canonicalQty)
                            .build();
                    ingredientsToAdd.add(newIngredient);
                    isChanged = true;
                }
            }
        }

        // 4) Lưu thay đổi nếu có bất kỳ cập nhật hoặc thêm mới nào
        if (isChanged) {
            // Thêm các món mới vào danh sách quản lý của entity
            if (!ingredientsToAdd.isEmpty()) {
                recipe.getIngredients().addAll(ingredientsToAdd);
            }

            recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            // Lưu Recipe (CascadeType.ALL sẽ tự động lưu các ingredients mới và update ingredients cũ)
            recipe = recipeRepository.saveAndFlush(recipe);
        }

        // 5) Trả về danh sách DTO (bao gồm cả cái mới thêm và cái vừa cập nhật)
        return recipe.getIngredients().stream()
                .map(RecipeIngredientItem::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Map<String, Integer> deleteIngredients(Long actorUserId, Long recipeId, DeleteIngredientsRequest req) {
        // 1) Tải User (actor) và Recipe, kiểm tra quyền
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // Kiểm tra Recipe đã bị xóa chưa
        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Không thể xóa nguyên liệu khỏi công thức đã bị xóa: " + recipeId);
        }

        Long ownerId = recipe.getUser().getUserId();
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // 2) Tải các RecipeIngredient cần xóa
        List<Long> idsToDelete = req.ingredientIds();
        if (idsToDelete == null || idsToDelete.isEmpty()) {
            throw new IllegalArgumentException("Danh sách ID nguyên liệu cần xóa không được rỗng.");
        }

        List<RecipeIngredient> ingredientsToDelete = recipeIngredientRepository.findAllById(idsToDelete);

        // 3) Kiểm tra xem tất cả ID có hợp lệ và thuộc về Recipe này không
        Set<Long> foundIds = ingredientsToDelete.stream()
                .map(RecipeIngredient::getId)
                .collect(Collectors.toSet());
        List<Long> missingOrInvalidIds = idsToDelete.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();

        if (!missingOrInvalidIds.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy các ID nguyên liệu sau: " + missingOrInvalidIds);
        }

        // Kiểm tra xem các nguyên liệu tìm thấy có thực sự thuộc về recipeId không
        for (RecipeIngredient ingredient : ingredientsToDelete) {
            if (!ingredient.getRecipe().getId().equals(recipeId)) {
                throw new AccessDeniedException("Nguyên liệu ID " + ingredient.getId() + " không thuộc về công thức này.");
            }
        }

        // 4) Thực hiện xóa
        // Sử dụng orphanRemoval=true trên Recipe.ingredients hoặc xóa trực tiếp qua repository
        recipeIngredientRepository.deleteAllInBatch(ingredientsToDelete); // Hiệu quả hơn khi xóa nhiều

        // Cập nhật thời gian update cho Recipe
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        recipeRepository.save(recipe);

        // 5) Trả về số lượng đã xóa
        return Map.of("deletedCount", ingredientsToDelete.size());
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

    /**
     * Helper private để gói gọn logic xây dựng RecipeResponse
     */
    @Override
    public RecipeResponse buildRecipeResponse(Recipe recipe, Long viewerUserId) {
        boolean isFavorited = false;
        Integer myRating = null;

        // Xử lý trường hợp viewerUserId là null (khách vãng lai)
        if (viewerUserId != null) {
            isFavorited = favoriteRepository.findByUser_UserIdAndRecipe_Id(viewerUserId, recipe.getId()).isPresent();
            myRating = ratingRepository.findByUser_UserIdAndRecipe_Id(viewerUserId, recipe.getId())
                    .map(RecipeRating::getScore)
                    .orElse(null);
        }

        // Lấy danh sách bình luận (CommentService đã xử lý quyền xem)
        List<CommentResponse> comments = commentService.getCommentsByRecipe(recipe.getId(), viewerUserId);

        return RecipeResponse.from(recipe, isFavorited, myRating, comments);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RecipeCardResponse> filterRecipes(User actor, Long filterUserId, Privacy privacy, Boolean deleted, int page, int size) {
        // 1. Cấu hình phân trang: Sắp xếp mặc định là mới nhất trước (createdAt DESC)
        int p = Math.max(0, page);
        int s = Math.min((size > 0 ? size : DEFAULT_SIZE), MAX_SIZE);
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. Logic phân quyền (Security Check)
        boolean isAdmin = (actor != null && actor.getRole() == Role.ADMIN);

        boolean isSelf = (actor != null && Objects.equals(actor.getUserId(), filterUserId));

        // Các biến filter thực tế
        Privacy finalPrivacy = privacy;
        Boolean finalDeleted = deleted;

        // Nếu không phải Admin VÀ không phải xem chính mình (tức là Khách hoặc User xem người khác)
        if (!isAdmin && !isSelf) {
            // Bắt buộc chỉ được xem PUBLIC
            finalPrivacy = Privacy.PUBLIC;
            // Bắt buộc không được xem công thức đã xóa
            finalDeleted = false;
        }

        // 3. Gọi Repository
        // Fix warning: Local variable 'finalUserId' is redundant -> Dùng trực tiếp filterUserId
        Page<Recipe> pageData = recipeRepository.findRecipesWithFilter(filterUserId, finalPrivacy, finalDeleted, pageable);

        // 4. Map sang DTO
        return PageResult.of(pageData.map(RecipeCardResponse::from));
    }

    @Override
    @Transactional
    public void restoreRecipe(Long actorUserId, Long recipeId) {
        // 1. Tìm công thức trong danh sách đã xóa mềm
        Recipe recipe = recipeRepository.findDeletedById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy công thức đã xóa với id: " + recipeId));

        // 2. Kiểm tra quyền: Chủ sở hữu hoặc Admin
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Long ownerId = recipe.getUser().getUserId();
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // 3. Khôi phục trạng thái Recipe
        // LOG INFO: Khôi phục
        log.info("Recipe Restored: ID={} by UserID={}", recipeId, actorUserId);
        recipe.setDeleted(false);
        recipe.setDeletedAt(null);
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        // 4. Kích hoạt lại ảnh của các bước (Re-activate images)
        // Lưu ý: Logic này giả định rằng lúc xóa mềm chúng ta đã set active=false cho tất cả ảnh.
        if (recipe.getSteps() != null) {
            for (RecipeStep step : recipe.getSteps()) {
                if (step.getImages() != null) {
                    for (RecipeStepImage image : step.getImages()) {
                        image.setActive(true);
                    }
                }
            }
        }

        // 5. Lưu Recipe
        recipeRepository.save(recipe);

        // 6. Khôi phục trạng thái 'isRecipeDeleted' trong các bảng liên quan
        shoppingListRepository.restoreByRecipeId(recipeId);
        favoriteRepository.restoreByRecipeId(recipeId);
        cookedHistoryRepository.restoreByRecipeId(recipeId);
    }

    @Override
    @Transactional
    public RecipeResponse updateNutrition(Long actorUserId, Long recipeId, UpdateNutritionRequest req) {
        // 1. Tìm User và Recipe
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        // 2. Kiểm tra đã xóa chưa
        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Không thể cập nhật công thức đã bị xóa: " + recipeId);
        }

        // 3. Kiểm tra quyền (Chủ sở hữu hoặc Admin)
        Long ownerId = recipe.getUser().getUserId();
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // 4. Cập nhật dữ liệu (Chỉ cập nhật nếu field không null)
        boolean isChanged = false;

        if (req.calories() != null) {
            recipe.setCalories(req.calories());
            isChanged = true;
        }

        if (req.servings() != null) {
            recipe.setServings(req.servings());
            isChanged = true;
        }

        // 5. Lưu và trả về
        if (isChanged) {
            recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            recipe = recipeRepository.saveAndFlush(recipe);
        }

        return buildRecipeResponse(recipe, actorUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeResponse> getPersonalizedSuggestions(Long currentUserId, PersonalizedSuggestionRequest req) { // Thêm currentUserId vào tham số

        // 1. Xây dựng Semantic Query (chỉ thêm các trường không null)
        StringBuilder semanticQuery = new StringBuilder("Tìm món ăn.");
        if (req.healthCondition() != null) semanticQuery.append(" Tốt cho: ").append(req.healthCondition()).append(".");
        if (req.dishCharacteristics() != null) semanticQuery.append(" Đặc điểm: ").append(req.dishCharacteristics()).append(".");
        if (req.mealType() != null) semanticQuery.append(" Bữa: ").append(req.mealType()).append(".");

        // 2. Lấy Vector
        List<Double> userVector = geminiApiClient.getEmbedding(semanticQuery.toString());
        List<Recipe> candidates;

        // 3. Lấy toàn bộ và lọc (Logic quan trọng bạn yêu cầu)
        List<Recipe> allRecipes = recipeRepository.findAll();

        Stream<Recipe> stream = allRecipes.stream()
                .filter(r -> !r.isDeleted()) // Chưa xóa
                .filter(r -> r.getEmbeddingVector() != null); // Đã có vector

        // *** LOGIC LỌC QUYỀN (Goal 3) ***
        // Lấy recipe nều: (Privacy là PUBLIC) HOẶC (Là chủ sở hữu recipe)
        stream = stream.filter(r ->
                r.getPrivacy() == Privacy.PUBLIC ||
                        (r.getUser() != null && r.getUser().getUserId().equals(currentUserId))
        );

        if (!userVector.isEmpty()) {
            // Có vector -> Tính điểm tương đồng
            candidates = stream
                    .map(r -> Map.entry(r, calculateCosineSimilarity(userVector, parseVector(r.getEmbeddingVector()))))
                    .sorted(Map.Entry.<Recipe, Double>comparingByValue().reversed())
                    .limit(20) // Lấy Top 20
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } else {
            // Không vector -> Lấy đại 20 cái mới nhất
            candidates = stream
                    .sorted(Comparator.comparing(Recipe::getCreatedAt).reversed())
                    .limit(20)
                    .collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            throw new EntityNotFoundException("Không tìm thấy món ăn phù hợp.");
        }

        // 4. Gọi AI xử lý (Dùng DTO Mới)
        List<AiMenuSuggestion> aiSuggestions = aiRecipeService.suggestPersonalizedMenu(req, candidates);

        // 5. Map về RecipeResponse
        List<RecipeResponse> finalResult = new ArrayList<>();
        for (AiMenuSuggestion aiItem : aiSuggestions) {
            Recipe original = candidates.stream()
                    .filter(r -> r.getId().equals(aiItem.getOriginalRecipeId()))
                    .findFirst().orElse(null);

            if (original != null) {
                // Map Ingredients
                List<RecipeResponse.IngredientDto> adjustedIngs = aiItem.getIngredients().stream()
                        .map(i -> RecipeResponse.IngredientDto.builder()
                                .name(i.getName())
                                .quantity(i.getQuantity())
                                .build())
                        .toList();

                // Build Response (Giữ ảnh/steps gốc, thay nội dung từ AI)
                RecipeResponse res = RecipeResponse.builder()
                        .id(original.getId())
                        .title(aiItem.getTitle())
                        .description(aiItem.getDescription()) // Lý do AI chọn
                        .imageUrl(original.getImageUrl())
                        .calories(aiItem.getCalories())
                        .servings(aiItem.getServings())
                        .difficulty(original.getDifficulty())
                        .prepareTime(original.getPrepareTime())
                        .cookTime(original.getCookTime())
                        .ownerName(original.getUser().getDisplayName()) // Thêm thông tin chủ sở hữu
                        .ingredients(adjustedIngs)
                        .steps(original.getSteps().stream().map(s ->
                                RecipeResponse.StepDto.builder()
                                        .stepNo(s.getStepNo())
                                        .content(s.getContent())
                                        // map images...
                                        .build()
                        ).toList())
                        .build();
                finalResult.add(res);
            }
        }
        return finalResult;
    }

    private double calculateCosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<Double> parseVector(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
