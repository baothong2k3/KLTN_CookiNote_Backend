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

import fit.kltn_cookinote_backend.dtos.request.RecipeStepUpdateRequest;
import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.RecipeStep;
import fit.kltn_cookinote_backend.entities.RecipeStepImage;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepImageRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecipeStepImageServiceImpl implements RecipeStepImageService {

    private final RecipeImageService recipeImageService;
    private final RecipeStepRepository stepRepository;
    private final RecipeStepImageRepository stepImageRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

    @PersistenceContext
    private EntityManager em;

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
        return RecipeResponse.from(reloaded);
    }
}
