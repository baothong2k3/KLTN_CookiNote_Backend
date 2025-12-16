/*
 * @ (#) CookedHistoryServiceImpl.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.response.CookedHistoryResponse;
import fit.kltn_cookinote_backend.entities.CookedHistory;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.entities.User;
import fit.kltn_cookinote_backend.enums.Privacy;
import fit.kltn_cookinote_backend.repositories.CookedHistoryRepository;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.repositories.UserRepository;
import fit.kltn_cookinote_backend.services.CookedHistoryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CookedHistoryServiceImpl implements CookedHistoryService {

    private final CookedHistoryRepository cookedHistoryRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

    @Override
    @Transactional
    public CookedHistoryResponse markRecipeAsCooked(Long userId, Long recipeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với id: " + userId));
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy công thức với id: " + recipeId));

        // Kiểm tra quyền xem công thức (quan trọng!)
        Long ownerId = recipe.getUser() != null ? recipe.getUser().getUserId() : null;
        if (!canView(recipe.getPrivacy(), ownerId, userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem công thức này.");
        }

        // Kiểm tra xem công thức có bị xóa không
        if (recipe.isDeleted()) {
            throw new IllegalArgumentException("Không thể đánh dấu công thức đã bị xóa là đã nấu.");
        }

        // Tạo bản ghi lịch sử mới
        CookedHistory history = CookedHistory.builder()
                .user(user)
                .recipe(recipe)
                .originalRecipeTitle(recipe.getTitle()) // Lưu tên gốc
                .isRecipeDeleted(false) // Mặc định là chưa xóa
                // cookedAt sẽ được tự động set bởi @CreationTimestamp
                .build();

        CookedHistory saved = cookedHistoryRepository.save(history);

        return CookedHistoryResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CookedHistoryResponse> getCookedHistory(Long userId, Long categoryId) {
        // Sử dụng method mới trong repository để hỗ trợ lọc
        List<CookedHistory> historyList = cookedHistoryRepository.findByUserAndFilter(userId, categoryId);

        return historyList.stream()
                .map(CookedHistoryResponse::from)
                .collect(Collectors.toList());
    }

    // Helper kiểm tra quyền xem (có thể đưa vào lớp Utils nếu dùng nhiều nơi)
    private boolean canView(Privacy privacy, Long ownerId, Long viewerId) {
        return switch (privacy) {
            case PUBLIC, SHARED -> true;
            case PRIVATE -> viewerId != null && viewerId.equals(ownerId);
        };
    }
}
