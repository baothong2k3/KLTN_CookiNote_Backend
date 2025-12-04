/*
 * @ (#) ScheduledTasksService.java    1.0    04/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 04/12/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.NutritionInfo;
import fit.kltn_cookinote_backend.entities.Recipe;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private final RecipeRepository recipeRepository;
    private final AiRecipeService aiRecipeService;
    // Inject TransactionManager để quản lý transaction thủ công
    private final PlatformTransactionManager transactionManager;

    // Chạy định kỳ mỗi 5 phút (300,000 ms)
    @Scheduled(fixedDelay = 300000)
    public void autoFillNutritionInfo() {
        log.info("Bắt đầu Job quét các recipe thiếu thông tin dinh dưỡng...");

        // Tạo TransactionTemplate từ Manager
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // 1. Lấy 20 công thức mỗi lần chạy để xử lý nhanh gọn
        Pageable limit = PageRequest.of(0, 20);
        List<Recipe> missingRecipes = recipeRepository.findRecipesMissingNutrition(limit);

        if (missingRecipes.isEmpty()) return;

        for (Recipe detachedRecipe : missingRecipes) {
            try {
                // --- XỬ LÝ TRANSACTION CỤC BỘ ---
                // Code trong execute sẽ chạy trong 1 transaction mới
                transactionTemplate.execute(status -> {
                    processSingleRecipe(detachedRecipe);
                    return null; // Lambda yêu cầu return
                });

                // Nghỉ ngắn giữa các item để tránh spam AI (Nằm ngoài transaction)
                Thread.sleep(2000);
            } catch (Exception e) {
                log.error("Lỗi job ID {}: {}", detachedRecipe.getId(), e.getMessage());
            }
        }
    }

    // Hàm này được gọi bên trong transactionTemplate.execute()
    private void processSingleRecipe(Recipe detachedRecipe) {
        // [QUAN TRỌNG] Tải lại Recipe từ DB để gắn vào Transaction hiện tại
        // Lúc này 'recipe' sẽ ở trạng thái Persistent (Managed), khắc phục lỗi LazyInitializationException
        Recipe recipe = recipeRepository.findById(detachedRecipe.getId()).orElse(null);

        if (recipe == null) {
            return; // Recipe có thể đã bị xóa
        }

        // Gọi AI service - Hibernate sẽ tự động fetch ingredients khi cần
        NutritionInfo info = aiRecipeService.estimateNutrition(recipe);

        if (info != null) {
            boolean updated = false;

            // Chỉ cập nhật nếu trường đó đang null (tránh ghi đè dữ liệu user nhập)
            if (recipe.getCalories() == null) {
                recipe.setCalories(info.calories());
                updated = true;
            }
            if (recipe.getServings() == null) {
                recipe.setServings(info.servings());
                updated = true;
            }

            if (updated) {
                recipeRepository.save(recipe);
                log.info("Đã cập nhật dinh dưỡng cho Recipe ID: {} (Calo: {}, Khẩu phần: {})",
                        recipe.getId(), info.calories(), info.servings());
            }
        }
    }
}