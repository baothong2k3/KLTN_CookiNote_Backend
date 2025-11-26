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

import fit.kltn_cookinote_backend.dtos.request.DeleteRecipeStepsRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeStepReorderRequest;
import fit.kltn_cookinote_backend.dtos.request.RecipeStepUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.RecipeStepItem;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.*;
import fit.kltn_cookinote_backend.services.*;
import fit.kltn_cookinote_backend.utils.ImageValidationUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
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
    private final CloudinaryService cloudinaryService;

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
    public RecipeStepItem updateStep(Long actorUserId, Long recipeId, Long stepId, RecipeStepUpdateRequest req) throws IOException {
        // 0) Load & kiểm quyền
        RecipeStep step = loadAndCheckStep(actorUserId, recipeId, stepId);

        Recipe recipe = step.getRecipe();
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        recipeRepository.save(recipe);

        // 1) Cập nhật nội dung, thời gian và tips
        if (req.content() != null) step.setContent(req.content());
        if (req.suggestedTime() != null) step.setSuggestedTime(req.suggestedTime());
        if (req.tips() != null) step.setTips(req.tips());

        // 2) Đảm bảo không trùng stepNo
        if (req.stepNo() != null) {
            Integer newNo = req.stepNo();
            Integer oldNo = step.getStepNo();
            if (!Objects.equals(newNo, oldNo)) {
                Optional<RecipeStep> conflictOpt = stepRepository.findByRecipe_IdAndStepNo(recipeId, newNo);
                if (conflictOpt.isPresent() && !Objects.equals(conflictOpt.get().getId(), stepId)) {
                    RecipeStep other = conflictOpt.get();
                    final int TEMP_NO = -1;
                    other.setStepNo(TEMP_NO);
                    stepRepository.saveAndFlush(other);
                    step.setStepNo(newNo);
                    stepRepository.saveAndFlush(step);
                    other.setStepNo(oldNo);
                    stepRepository.save(other);
                } else {
                    step.setStepNo(newNo);
                    stepRepository.save(step);
                }
            } else {
                stepRepository.save(step);
            }
        } else {
            stepRepository.save(step);
        }

        // 3) Xử lý ảnh
        List<RecipeStepImage> existingImages = stepImageRepository.findByStep_Id(stepId);
        // Nếu req.keepUrls() là null hoặc rỗng -> nghĩa là xóa hết ảnh cũ (hoặc client không gửi gì)
        // Lưu ý: Nếu client muốn giữ ảnh, bắt buộc phải gửi list này.
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

        // 3.3) Thêm ảnh mới
        if (req.addFiles() != null && !req.addFiles().isEmpty()) {
            for (MultipartFile f : req.addFiles()) ImageValidationUtils.validateImage(f);
            List<String> newUrls = recipeImageService.uploadStepImages(recipeId, stepId, req.addFiles());
            if (!newUrls.isEmpty()) {
                List<RecipeStepImage> toSave = new ArrayList<>();
                for (String url : newUrls) {
                    toSave.add(RecipeStepImage.builder().step(step).imageUrl(url).build());
                }
                stepImageRepository.saveAll(toSave);
                // Cập nhật list ảnh trong memory object để trả về đúng ngay lập tức
                if (step.getImages() == null) step.setImages(new ArrayList<>());
                step.getImages().addAll(toSave);
            }
        }

        em.flush();
        em.refresh(step); // Làm mới entity từ DB để đảm bảo có dữ liệu mới nhất (ảnh, thứ tự)

        // 4) Trả về StepItem thay vì RecipeResponse
        return RecipeStepItem.from(step);
    }

    @Override
    @Transactional
    public List<RecipeStepItem> addStep(Long actorUserId, Long recipeId, String content, Integer suggestedTime, String tips, List<MultipartFile> addFiles) throws IOException {
        // 1) Tải công thức và kiểm tra quyền
        Recipe recipe = loadAndCheckRecipe(actorUserId, recipeId);

        // 2) Cập nhật thời gian cho Recipe
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));

        // 3) Tính stepNo tiếp theo
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

        // 6) Lưu recipe (để cập nhật updatedAt)
        // Không cần add vào list recipe.getSteps() thủ công vì query bên dưới sẽ lấy lại từ DB
        recipeRepository.save(recipe);

        em.flush();
        em.clear(); // Clear cache để đảm bảo query lấy dữ liệu mới nhất

        // 7) Lấy danh sách steps mới nhất đã sắp xếp và trả về
        List<RecipeStep> allSteps = stepRepository.findByRecipe_IdOrderByStepNoAsc(recipeId);

        // Map entity sang DTO (nhớ tải lazy collection nếu cần thiết, nhưng findBy... thường lấy đủ nếu config fetch mode hoặc batch size,
        // hoặc RecipeStepItem.from sẽ kích hoạt proxy nếu còn trong session)
        return allSteps.stream()
                .map(step -> {
                    // Đảm bảo images được load nếu đang lazy (mặc dù trong transaction thì hibernate tự xử lý)
                    Hibernate.initialize(step.getImages());
                    return RecipeStepItem.from(step);
                })
                .toList();
    }

    @Override
    @Transactional
    public List<RecipeStepItem> reorderSteps(Long actorUserId, Long recipeId, RecipeStepReorderRequest req) {
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

        // 3.2) Check unique and continuous sequence
        int n = existingSteps.size();
        if (newStepNos.size() != n) {
            throw new IllegalArgumentException("New step numbers (newStepNo) must be unique.");
        }
        for (int i = 1; i <= n; i++) {
            if (!newStepNos.contains(i)) {
                throw new IllegalArgumentException("New step numbers (newStepNo) must form a continuous sequence from 1 to " + n + ".");
            }
        }

        // 4) Phase 1: Temporary negative numbering to avoid unique constraint violation
        Map<Integer, Long> currentStepNoToIdMap = existingSteps.stream()
                .collect(Collectors.toMap(RecipeStep::getStepNo, RecipeStep::getId));

        int tempStepNoCounter = -1;

        for (RecipeStep step : existingSteps) {
            Integer targetStepNo = requestedOrderMap.get(step.getId());
            Long stepIdCurrentlyAtTarget = currentStepNoToIdMap.get(targetStepNo);

            if (stepIdCurrentlyAtTarget != null && !stepIdCurrentlyAtTarget.equals(step.getId())) {
                if (requestedOrderMap.containsKey(stepIdCurrentlyAtTarget)) {
                    step.setStepNo(tempStepNoCounter--);
                }
            }
        }
        stepRepository.saveAllAndFlush(existingSteps);

        // 5) Phase 2: Update final stepNo
        for (RecipeStep step : existingSteps) {
            Integer finalStepNo = requestedOrderMap.get(step.getId());
            step.setStepNo(finalStepNo);
        }
        stepRepository.saveAll(existingSteps);

        // 6) Update Recipe timestamp
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        recipeRepository.save(recipe);

        // 7) Flush
        em.flush();
        em.clear(); // Xóa cache để đảm bảo lấy dữ liệu mới nhất từ DB

        // 8) THAY ĐỔI LOGIC TRẢ VỀ Ở ĐÂY
        // Lấy lại danh sách các bước, sắp xếp theo stepNo tăng dần
        List<RecipeStep> reorderedSteps = stepRepository.findByRecipe_IdOrderByStepNoAsc(recipeId);

        // Map sang DTO
        return reorderedSteps.stream()
                .map(step -> {
                    Hibernate.initialize(step.getImages()); // Đảm bảo ảnh được load
                    return RecipeStepItem.from(step);
                })
                .toList();
    }

    @Override
    @Transactional
    public Map<String, Integer> deleteSteps(Long actorUserId, Long recipeId, DeleteRecipeStepsRequest req) {
        // 1) Tải Recipe và kiểm tra quyền
        Recipe recipe = loadAndCheckRecipe(actorUserId, recipeId);

        List<Long> idsToDelete = req.stepIds();
        if (idsToDelete == null || idsToDelete.isEmpty()) {
            throw new IllegalArgumentException("Danh sách ID các bước cần xóa không được rỗng.");
        }

        // 2) Tải các Step entity cần xóa (bao gồm cả ảnh của chúng để chuẩn bị xóa)
        // Chúng ta cần tải các ảnh (images) của step một cách rõ ràng (EAGER)
        List<RecipeStep> stepsToDelete = stepRepository.findAllById(idsToDelete);
        stepsToDelete.forEach(step -> Hibernate.initialize(step.getImages())); // Tải EAGER

        // 3) Validate và thu thập Public IDs
        final List<String> publicIdsToDeleteOnCloudinary = new ArrayList<>();
        Set<Long> foundIds = new HashSet<>();

        for (RecipeStep step : stepsToDelete) {
            // 3.1) Kiểm tra xem step có thuộc đúng recipe không
            if (!step.getRecipe().getId().equals(recipeId)) {
                throw new AccessDeniedException("Bước ID " + step.getId() + " không thuộc về công thức này.");
            }
            foundIds.add(step.getId());

            // 3.2) Thu thập public ID từ ảnh của step
            if (step.getImages() != null) {
                for (RecipeStepImage image : step.getImages()) {
                    String publicId = cloudinaryService.extractPublicIdFromUrl(image.getImageUrl());
                    if (StringUtils.hasText(publicId)) {
                        publicIdsToDeleteOnCloudinary.add(publicId);
                    }
                }
            }
        }

        // 3.3) Kiểm tra xem có ID nào yêu cầu xóa mà không tìm thấy không
        List<Long> missingIds = idsToDelete.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new EntityNotFoundException("Không tìm thấy các bước có ID: " + missingIds);
        }

        // 4) Đăng ký xóa ảnh trên Cloudinary (SAU KHI COMMIT DB)
        if (!publicIdsToDeleteOnCloudinary.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String publicId : publicIdsToDeleteOnCloudinary) {
                        cloudinaryService.safeDeleteByPublicId(publicId);
                    }
                }
            });
        }

        // 5) Xóa các Step khỏi DB
        // Do RecipeStep có cascade = ALL và orphanRemoval = true
        // nên việc xóa RecipeStep cũng sẽ tự động xóa RecipeStepImage khỏi DB.
        stepRepository.deleteAll(stepsToDelete);
        em.flush(); // Đẩy các thay đổi xóa xuống DB

        // 6) Đánh số lại thứ tự (re-number) các bước còn lại
        List<RecipeStep> remainingSteps = stepRepository.findByRecipe_IdOrderByStepNoAsc(recipeId);
        int newStepNo = 1;
        boolean reordered = false;
        for (RecipeStep step : remainingSteps) {
            if (step.getStepNo() != newStepNo) {
                step.setStepNo(newStepNo);
                reordered = true;
            }
            newStepNo++;
        }

        if (reordered) {
            stepRepository.saveAll(remainingSteps);
        }

        // 7) Cập nhật thời gian cho Recipe
        recipe.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        recipeRepository.save(recipe);

        // 8) Trả về kết quả
        return Map.of(
                "deletedCount", stepsToDelete.size(),
                "reorderedCount", remainingSteps.size()
        );
    }
}
