/*
 * @ (#) ImageServiceImpl.java    1.0    21/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 21/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.entities.RecipeCoverImageHistory;
import fit.kltn_cookinote_backend.entities.RecipeStepImage;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Role;
import fit.kltn_cookinote_backend.repositories.RecipeCoverImageHistoryRepository;
import fit.kltn_cookinote_backend.repositories.RecipeStepImageRepository;
import fit.kltn_cookinote_backend.services.CloudinaryService;
import fit.kltn_cookinote_backend.services.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final RecipeCoverImageHistoryRepository coverImageRepo;
    private final RecipeStepImageRepository stepImageRepo;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional
    public Map<String, Integer> deleteInactiveImages(User actor, List<Long> coverImageIds, List<Long> stepImageIds) {

        final List<String> publicIdsToDelete = new ArrayList<>();
        final List<RecipeCoverImageHistory> coversToDelete = new ArrayList<>();
        final List<RecipeStepImage> stepsToDelete = new ArrayList<>();

        // 1. Xử lý ảnh bìa (Cover Images)
        if (coverImageIds != null && !coverImageIds.isEmpty()) {
            List<RecipeCoverImageHistory> covers = coverImageRepo.findByIdInWithRecipeAndUser(coverImageIds);

            for (RecipeCoverImageHistory cover : covers) {
                // Kiểm tra quyền: Admin hoặc chủ sở hữu
                Long ownerId = cover.getRecipe().getUser().getUserId();
                if (actor.getRole() != Role.ADMIN && !Objects.equals(actor.getUserId(), ownerId)) {
                    throw new AccessDeniedException("Bạn không có quyền xóa ảnh bìa ID: " + cover.getId());
                }

                // Kiểm tra điều kiện active: false
                if (cover.isActive()) {
                    throw new IllegalArgumentException("Ảnh bìa ID: " + cover.getId() + " vẫn đang hoạt động. Chỉ có thể xóa ảnh 'active: false'.");
                }

                // Thêm vào danh sách chờ xóa
                coversToDelete.add(cover);
                String publicId = cloudinaryService.extractPublicIdFromUrl(cover.getImageUrl());
                if (StringUtils.hasText(publicId)) {
                    publicIdsToDelete.add(publicId);
                }
            }
        }

        // 2. Xử lý ảnh bước (Step Images)
        if (stepImageIds != null && !stepImageIds.isEmpty()) {
            List<RecipeStepImage> steps = stepImageRepo.findByIdInWithStepRecipeAndUser(stepImageIds);

            for (RecipeStepImage stepImage : steps) {
                // Kiểm tra quyền: Admin hoặc chủ sở hữu
                Long ownerId = stepImage.getStep().getRecipe().getUser().getUserId();
                if (actor.getRole() != Role.ADMIN && !Objects.equals(actor.getUserId(), ownerId)) {
                    throw new AccessDeniedException("Bạn không có quyền xóa ảnh bước ID: " + stepImage.getId());
                }

                // Kiểm tra điều kiện active: false
                if (stepImage.isActive()) {
                    throw new IllegalArgumentException("Ảnh bước ID: " + stepImage.getId() + " vẫn đang hoạt động. Chỉ có thể xóa ảnh 'active: false'.");
                }

                // Thêm vào danh sách chờ xóa
                stepsToDelete.add(stepImage);
                String publicId = cloudinaryService.extractPublicIdFromUrl(stepImage.getImageUrl());
                if (StringUtils.hasText(publicId)) {
                    publicIdsToDelete.add(publicId);
                }
            }
        }

        // 3. Xóa khỏi cơ sở dữ liệu
        if (!coversToDelete.isEmpty()) {
            coverImageRepo.deleteAll(coversToDelete);
        }
        if (!stepsToDelete.isEmpty()) {
            stepImageRepo.deleteAll(stepsToDelete);
        }

        // 4. Đăng ký tác vụ xóa Cloudinary (chỉ chạy khi commit DB thành công)
        if (!publicIdsToDelete.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String publicId : publicIdsToDelete) {
                        cloudinaryService.safeDeleteByPublicId(publicId);
                    }
                }
            });
        }

        // 5. Trả về kết quả
        Map<String, Integer> result = new HashMap<>();
        result.put("deletedCovers", coversToDelete.size());
        result.put("deletedSteps", stepsToDelete.size());
        return result;
    }
}
