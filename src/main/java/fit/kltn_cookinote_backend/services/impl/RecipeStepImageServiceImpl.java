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
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepImageRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.CloudinaryService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final CloudinaryService cloudinaryService;
    private final RecipeRepository recipeRepository;

    @PersistenceContext
    private EntityManager em;

    private void ensureOwnerOrAdmin(Long actorId, Long ownerId, Role actorRole) {
        if (actorRole == Role.ADMIN) return;
        if (!actorId.equals(ownerId)) throw new AccessDeniedException("Chỉ chủ sở hữu hoặc ADMIN mới được chỉnh sửa.");
    }

    @Override
    @Transactional
    public List<String> addImagesToStep(Long actorUserId, Long recipeId, Long stepId, List<MultipartFile> files) throws IOException {
        RecipeStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Step không tồn tại: " + stepId));

        if (!Objects.equals(step.getRecipe().getId(), recipeId)) {
            throw new IllegalArgumentException("Step không thuộc về Recipe này.");
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));

        Long ownerId = stepRepository.findOwnerIdByStepId(stepId);
        if (ownerId == null) throw new EntityNotFoundException("Step không tồn tại: " + stepId);

        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

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
        RecipeStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Step không tồn tại: " + stepId));
        if (!Objects.equals(step.getRecipe().getId(), recipeId)) {
            throw new IllegalArgumentException("Step không thuộc về Recipe này.");
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Tài khoản không tồn tại: " + actorUserId));
        Long ownerId = stepRepository.findOwnerIdByStepId(stepId);
        ensureOwnerOrAdmin(actorUserId, ownerId, actor.getRole());

        // 1) Cập nhật nội dung
        if (req.content() != null) {
            step.setContent(req.content());
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

        // 3) Ảnh: giữ/xoá/thêm (≤ 5 ảnh/step)
        List<RecipeStepImage> existing = stepImageRepository.findByStep_Id(stepId);
        Set<String> keepSet = new HashSet<>(Optional.ofNullable(req.keepUrls()).orElse(List.of()));

        // 3.1 Ảnh cần xoá
        List<RecipeStepImage> toDelete = existing.stream()
                .filter(img -> !keepSet.contains(img.getImageUrl()))
                .toList();

        int keepCount = existing.size() - toDelete.size();
        int addCount = Optional.ofNullable(req.addFiles()).map(List::size).orElse(0);
        if (keepCount + addCount > 5) {
            throw new IllegalArgumentException("Mỗi step tối đa 5 ảnh (giữ " + keepCount + ", thêm " + addCount + ").");
        }

        // 3.2 Xoá DB ngay, Cloudinary sau commit
        if (!toDelete.isEmpty()) {
            List<Long> delIds = toDelete.stream().map(RecipeStepImage::getId).toList();
            stepImageRepository.deleteByIdIn(delIds);

            for (RecipeStepImage img : toDelete) {
                String pid = cloudinaryService.extractPublicIdFromUrl(img.getImageUrl());
                if (org.springframework.util.StringUtils.hasText(pid)) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            cloudinaryService.safeDeleteByPublicId(pid);
                        }
                    });
                }
            }
        }

        // 3.3 Thêm ảnh mới
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

        // 4) Reload & trả về RecipeResponse
        Recipe reloaded = recipeRepository.findDetailById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Recipe không tồn tại: " + recipeId));
        return RecipeResponse.from(reloaded);
    }
}
