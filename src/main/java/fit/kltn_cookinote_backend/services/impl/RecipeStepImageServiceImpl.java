/*
 * @ (#) RecipeStepImageServiceImpl.java    1.0    13/09/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 13/09/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.RecipeStepReorderRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeStepUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.*;
import fit.kltn_cookinote_backend.services.RecipeImageService;
import fit.kltn_cookinote_backend.services.RecipeStepImageService;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeStepImageServiceImpl implements RecipeStepImageService {

    private final RecipeImageService recipeImageService;
    private final RecipeStepRepository stepRepository;
    private final RecipeStepImageRepository stepImageRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final FavoriteRepository favoriteRepository;
    private final RecipeRatingRepository ratingRepository;

    @PersistenceContext
    private EntityManager em;

    // (Helper mới để kiểm tra quyền sở hữu Recipe)
    private Recipe loadAndCheckRecipe(Long actorUserId, Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Không thể chỉnh sửa công thức đã bị xóa: " + recipeId);
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Long ownerId = recipe.getUser().getUserId();
        // Chỉ chủ sở hữu (hoặc admin) mới được thêm
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        return recipe;
    }

    private void ensureOwnerOrAdmin(Long actorId, Long ownerId, Role actorRole) {
        if (actorRole == Role.ADMIN) return;
        if (!actorId.equals(ownerId)) throw new AccessDeniedException("Chỉ chủ sở hữu hoặc ADMIN mới được chỉnh sửa.");
    }

    private RecipeStep loadAndCheckStep(Long actorUserId, Long recipeId, Long stepId) {
        RecipeStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Step không tồn tại: " + stepId));
        Recipe recipe = step.getRecipe();

        if (!Objects.equals(recipe.getId(), recipeId)) {
            throw new IllegalArgumentException("Step không thuộc về Recipe này.");
        }

        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Công thức của bước này đã bị xóa: " + recipeId);
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Long ownerId = recipe.getUser().getUserId();

        if (recipe.getPrivacy() == Privacy.PRIVATE) {
            if (!actorUserId.equals(ownerId)) {
                throw new AccessDeniedException("Chỉ chủ sở hữu mới có quyền chỉnh sửa công thức riêng tư.");
            }
        } else {
            ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());
        }

        return step;
    }


    @Override
    @Transactional
    public List<String> addImagesToStep(Long actorUserId, Long recipeId, Long stepId, List<MultipartFile> files) throws IOException {
        RecipeStep step = loadAndCheckStep(actorUserId, recipeId, stepId);

        long current = stepImageRepository.countByStep_Id(stepId);
        int incoming = (files == null) ? 0 : files.size();
        if (incoming == 0) throw new IllegalArgumentException("Danh sách ảnh trống.");
        if (current + incoming > 5) {
            throw new IllegalArgumentException("Mỗi step tối đa 5 ảnh (đang có " + current + ", upload " + incoming + ").");
        }

        // Upload
        List<String> urls = recipeImageService.uploadStepImages(recipeId, stepId, files);

        // Persist
        List<RecipeStepImage> toSave = new ArrayList<>();
        for (String url : urls) {
            RecipeStepImage img = RecipeStepImage.builder()
                    .step(step)
                    .imageUrl(url)
                    .build();
            toSave.add(img);
        }
        stepImageRepository.saveAll(toSave);

        // đồng bộ list trong entity (nếu có mapping)
        if (step.getImages() != null) {
            step.getImages().addAll(toSave);
        }

        return urls;
    }

    @Override
    @Transactional
    public RecipeResponse updateStep(Long actorUserId, Long recipeId, Long stepId, RecipeStepUpdateRequest req) throws IOException {
        // 0) Load & kiểm quyền
        RecipeStep step = loadAndCheckStep(actorUserId, recipeId, stepId);

        Recipe recipe = step.getRecipe();
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        recipeRepository.save(recipe);

        // 1) Cập nhật nội dung, thời gian và tips
        if (req.content() != null) {
            step.setContent(req.content());
        }
        if (req.suggestedTime() != null) {
            step.setSuggestedTime(req.suggestedTime());
        }
        if (req.tips() != null) {
            step.setTips(req.tips());
        }

        // 2) Đảm bảo không trùng stepNo (swap nếu cần)
        if (req.stepNo() != null) {
            Integer newNo = req.stepNo();
            Integer oldNo = step.getStepNo();
            if (!Objects.equals(newNo, oldNo)) {
                Optional<RecipeStep> conflictOpt = stepRepository.findByRecipe_IdAndStepNo(recipeId, newNo);
                if (conflictOpt.isPresent() && !Objects.equals(conflictOpt.get().getId(), stepId)) {
                    // Có xung đột: dùng "đệm" để tránh vi phạm unique tại thời điểm update
                    RecipeStep other = conflictOpt.get();

                    // (A) đẩy other sang số đệm tạm thời
                    final int TEMP_NO = -1; // cột step_no nên NOT NULL, -1 vẫn hợp lệ và không đụng unique
                    other.setStepNo(TEMP_NO);
                    stepRepository.saveAndFlush(other); // flush để ghi ngay, tránh đụng unique khi set 'step' sang newNo

                    // (B) gán step sang newNo
                    step.setStepNo(newNo);
                    stepRepository.saveAndFlush(step);

                    // (C) gán other về oldNo
                    other.setStepNo(oldNo);
                    stepRepository.save(other);
                } else {
                    // Không xung đột, set trực tiếp
                    step.setStepNo(newNo);
                    stepRepository.save(step);
                }
            } else {
                // stepNo không đổi → vẫn lưu nếu có content thay đổi
                stepRepository.save(step);
            }
        } else {
            // Không đổi stepNo → vẫn lưu nếu có content thay đổi
            stepRepository.save(step);
        }

        // 3) Xử lý ảnh
        List<RecipeStepImage> existingImages = stepImageRepository.findByStep_Id(stepId);
        Set<String> keepUrlsSet = new HashSet<>(Optional.ofNullable(req.keepUrls()).orElse(List.of()));

        // 3.1) Vô hiệu hóa (soft-delete) các ảnh không được giữ lại
        List<RecipeStepImage> toDeactivate = existingImages.stream()
                .filter(img -> img.isActive() && !keepUrlsSet.contains(img.getImageUrl()))
                .toList();

        if (!toDeactivate.isEmpty()) {
            for (RecipeStepImage img : toDeactivate) {
                img.setActive(false);
            }
            stepImageRepository.saveAll(toDeactivate);
        }

        // 3.2) Kiểm tra giới hạn số lượng ảnh
        long activeImageCount = existingImages.stream().filter(RecipeStepImage::isActive).count() - toDeactivate.size();
        int addCount = Optional.ofNullable(req.addFiles()).map(List::size).orElse(0);
        if (activeImageCount + addCount > 5) {
            throw new IllegalArgumentException("Mỗi step tối đa 5 ảnh (hiện có " + activeImageCount + ", thêm " + addCount + ").");
        }

        // 3.3) Thêm ảnh mới (các ảnh này sẽ có active=true mặc định)
        if (req.addFiles() != null && !req.addFiles().isEmpty()) {
            for (MultipartFile f : req.addFiles()) ImageValidationUtils.validateImage(f);
            List<String> newUrls = recipeImageService.uploadStepImages(recipeId, stepId, req.addFiles());
            if (!newUrls.isEmpty()) {
                List<RecipeStepImage> toSave = new ArrayList<>();
                for (String url : newUrls) {
                    toSave.add(RecipeStepImage.builder().step(step).imageUrl(url).build());
                }
                stepImageRepository.saveAll(toSave);
            }
        }

        em.flush();
        em.clear();

        // 4) Tải lại và trả về
        Recipe reloaded = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        return buildRecipeResponse(actorUserId, reloaded);
    }

    @Override
    @Transactional
    public RecipeResponse addStep(Long actorUserId, Long recipeId, String content, Integer suggestedTime, String tips, List<MultipartFile> addFiles) throws IOException {
        // 1) Tải công thức và kiểm tra quyền
        Recipe recipe = loadAndCheckRecipe(actorUserId, recipeId);

        // 2) Cập nhật thời gian cho Recipe
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        // 3) Tính stepNo tiếp theo (thêm vào cuối)
        int newStepNo = recipe.getSteps().stream()
                .mapToInt(RecipeStep::getStepNo)
                .max()
                .orElse(0) + 1;

        // 4) Tạo và lưu Step mới để lấy ID
        RecipeStep newStep = RecipeStep.builder()
                .recipe(recipe)
                .stepNo(newStepNo)
                .content(content)
                .suggestedTime(suggestedTime)
                .tips(tips)
                .images(new ArrayList<>()) // Khởi tạo list
                .build();
        stepRepository.saveAndFlush(newStep); // Lưu ngay để lấy ID

        // 5) Xử lý ảnh nếu có
        if (addFiles != null && !addFiles.isEmpty()) {
            // 5.1) Validate
            for (MultipartFile f : addFiles) ImageValidationUtils.validateImage(f);
            if (addFiles.size() > 5) {
                throw new IllegalArgumentException("Mỗi step tối đa 5 ảnh (bạn đang thêm " + addFiles.size() + ").");
            }

            // 5.2) Upload
            List<String> newUrls = recipeImageService.uploadStepImages(recipeId, newStep.getId(), addFiles);

            // 5.3) Lưu ảnh vào DB và liên kết với Step
            if (!newUrls.isEmpty()) {
                List<RecipeStepImage> toSave = new ArrayList<>();
                for (String url : newUrls) {
                    toSave.add(RecipeStepImage.builder().step(newStep).imageUrl(url).build());
                }
                stepImageRepository.saveAll(toSave);
                newStep.getImages().addAll(toSave);
            }
        }

        // 6) Thêm bước mới vào danh sách của recipe (để response trả về)
        recipe.getSteps().add(newStep);
        recipeRepository.save(recipe); // Lưu recipe (để cập nhật updatedAt)

        em.flush();

        // 7) Tải lại toàn bộ và trả về
        Recipe reloaded = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));

        return buildRecipeResponse(actorUserId, reloaded);
    }

    @Override
    @Transactional
    public RecipeResponse reorderSteps(Long actorUserId, Long recipeId, RecipeStepReorderRequest req) {
        // 1) Load Recipe and check permissions
        Recipe recipe = loadAndCheckRecipe(actorUserId, recipeId);

        // 2) Load all existing Steps for the Recipe
        List<RecipeStep> existingSteps = stepRepository.findByRecipe_IdOrderByStepNoAsc(recipeId);
        Map<Long, RecipeStep> existingStepsMap = existingSteps.stream()
                .collect(Collectors.toMap(RecipeStep::getId, Function.identity()));

        // 3) Validate Input
        List<RecipeStepReorderRequest.StepOrder> requestedOrder = req.steps();
        Map<Long, Integer> requestedOrderMap = requestedOrder.stream()
                .collect(Collectors.toMap(RecipeStepReorderRequest.StepOrder::stepId, RecipeStepReorderRequest.StepOrder::newStepNo));
        Set<Integer> newStepNos = new HashSet<>(requestedOrderMap.values());

        // 3.1) Check size consistency
        if (requestedOrderMap.size() != existingStepsMap.size() || !requestedOrderMap.keySet().containsAll(existingStepsMap.keySet())) {
            throw new IllegalArgumentException("Reorder request must contain all (" + existingStepsMap.size() + ") existing steps for the recipe and no extras.");
        }

        // 3.2) Kiểm tra new step numbers là duy nhất và tạo thành dãy liên tục từ 1..N
        int n = existingSteps.size();
        if (newStepNos.size() != n) {
            throw new IllegalArgumentException("New step numbers (newStepNo) must be unique.");
        }
        for (int i = 1; i <= n; i++) {
            if (!newStepNos.contains(i)) {
                throw new IllegalArgumentException("New step numbers (newStepNo) must form a continuous sequence from 1 to " + n + ".");
            }
        }

        // 4) Phase 1: Di chuyển các bước sang số âm tạm thời NẾU vị trí mục tiêu hiện đang bị chiếm bởi
        //    một bước khác cũng đang được di chuyển đi chỗ khác.
        Map<Integer, Long> currentStepNoToIdMap = existingSteps.stream()
                .collect(Collectors.toMap(RecipeStep::getStepNo, RecipeStep::getId));

        int tempStepNoCounter = -1; // Start temporary numbering from -1

        for (RecipeStep step : existingSteps) {
            Integer targetStepNo = requestedOrderMap.get(step.getId());
            Long stepIdCurrentlyAtTarget = currentStepNoToIdMap.get(targetStepNo);

            // Check if the target position is occupied by another step that is ALSO moving
            if (stepIdCurrentlyAtTarget != null && !stepIdCurrentlyAtTarget.equals(step.getId())) {
                // Check if the occupying step is actually part of this reorder request (should always be true based on validation)
                if (requestedOrderMap.containsKey(stepIdCurrentlyAtTarget)) {
                    // Assign temporary negative step number to avoid conflict
                    step.setStepNo(tempStepNoCounter--);
                }
            }
        }
        // Immediately flush changes with temporary negative numbers
        stepRepository.saveAllAndFlush(existingSteps);


        // 5) Phase 2: Cập nhật stepNo cuối cùng cho tất cả các bước
        for (RecipeStep step : existingSteps) {
            Integer finalStepNo = requestedOrderMap.get(step.getId());
            step.setStepNo(finalStepNo);
        }
        // Save the final state (Hibernate/JPA will handle updates)
        stepRepository.saveAll(existingSteps);

        // 6) Update Recipe timestamp
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        recipeRepository.save(recipe);

        // 7) Flush again to ensure all changes are written before reloading
        em.flush();

        // 8) Reload the detailed Recipe to return the updated state
        Recipe reloaded = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe not found after update: " + recipeId));

        return buildRecipeResponse(actorUserId, reloaded);
    }

    /**
     * Phương thức helper private để lấy isFavorited, myRating và tạo RecipeResponse.
     *
     * @param actorUserId ID của người dùng thực hiện hành động (để kiểm tra favorite/rating).
     * @param recipe      Đối tượng Recipe đã được cập nhật và tải lại.
     * @return RecipeResponse hoàn chỉnh.
     */
    private RecipeResponse buildRecipeResponse(Long actorUserId, Recipe recipe) {
        boolean isFavorited = favoriteRepository.findByUser_UserIdAndRecipe_Id(actorUserId, recipe.getId()).isPresent();
        Integer myRating = ratingRepository.findByUser_UserIdAndRecipe_Id(actorUserId, recipe.getId())
                .map(RecipeRating::getScore)
                .orElse(null);
        return RecipeResponse.from(recipe, isFavorited, myRating);
    }
}
