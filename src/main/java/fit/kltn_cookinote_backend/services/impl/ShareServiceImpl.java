/*
 * @ (#) ShareServiceImpl.java    1.0    28/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 28/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.RecipeResponse;
import fit.kltn_cookinote_backend.dtos.response.ShareResponse;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.Share;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.*;
import fit.kltn_cookinote_backend.services.RecipeService;
import fit.kltn_cookinote_backend.services.ShareService;
import fit.kltn_cookinote_backend.utils.QrCodeUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final ShareRepository shareRepository;
    private final RecipeService recipeService;

    @Value("${app.baseUrl}")
    private String baseUrl;

    @Override
    @Transactional
    public ShareResponse createShareLink(Long sharerUserId, Long recipeId) {
        User sharer = userRepository.findById(sharerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Người dùng không tồn tại: " + sharerUserId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Công thức không tồn tại: " + recipeId));

        // Kiểm tra công thức đã bị xóa chưa
        if (recipe.isDeleted()) {
            throw new EntityNotFoundException("Không thể chia sẻ công thức đã bị xóa.");
        }

        // Kiểm tra quyền chia sẻ
        boolean canShare = false;
        Privacy currentPrivacy = recipe.getPrivacy();

        if (currentPrivacy == Privacy.PUBLIC || currentPrivacy == Privacy.SHARED) {
            canShare = true;
        } else if (currentPrivacy == Privacy.PRIVATE) {
            // Chỉ chủ sở hữu mới được share PRIVATE
            if (Objects.equals(recipe.getUser().getUserId(), sharerUserId)) {
                // Nâng cấp lên SHARED khi chia sẻ lần đầu
                recipe.setPrivacy(Privacy.SHARED);
                recipeRepository.save(recipe); // Lưu thay đổi trạng thái
                canShare = true;
            }
        }

        if (!canShare) {
            throw new AccessDeniedException("Bạn không có quyền chia sẻ công thức này.");
        }

        // Tạo share code mới unique
        String shareCode;
        do {
            shareCode = UUID.randomUUID().toString().substring(0, 8); // Lấy 8 ký tự đầu UUID
        } while (shareRepository.findByShareCode(shareCode).isPresent()); // Đảm bảo unique

        // Lưu vào DB
        Share newShare = Share.builder()
                .recipe(recipe)
                .user(sharer) // Lưu người tạo link share
                .shareCode(shareCode)
                .build();
        shareRepository.save(newShare);

        String shareUrl = baseUrl + "/recipes/shared/" + shareCode;

        // Tạo mã QR
        String qrCodeBase64 = QrCodeUtil.generateQrCodeBase64(shareUrl, 200, 200);
        if (qrCodeBase64 == null) {
            throw new RuntimeException("Không thể tạo mã QR cho việc chia sẻ.");
        }

        return ShareResponse.builder()
                .shareCode(shareCode)
                .shareUrl(shareUrl)
                .qrCodeBase64(qrCodeBase64)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeResponse getRecipeByShareCode(String shareCode, Long viewerUserIdOrNull) {
        Share share = shareRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new EntityNotFoundException("Mã chia sẻ không hợp lệ hoặc đã hết hạn."));

        Recipe recipe = share.getRecipe();
        if (recipe == null || recipe.isDeleted()) {
            throw new EntityNotFoundException("Công thức được chia sẻ không còn tồn tại.");
        }

        // Kiểm tra lại privacy
        if (recipe.getPrivacy() == Privacy.PRIVATE) {
            throw new AccessDeniedException("Công thức này đã được chuyển về chế độ riêng tư.");
        }

        return recipeService.buildRecipeResponse(recipe, viewerUserIdOrNull);
    }
}
